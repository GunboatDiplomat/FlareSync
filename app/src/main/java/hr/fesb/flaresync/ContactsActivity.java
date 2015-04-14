package hr.fesb.flaresync;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.widget.AdapterView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class ContactsActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

    private SimpleCursorAdapter mAdapter;
    private Firebase userRoot;
    private SQLController mDB;
    private String currentRow;
    private static final String[] FROM = {Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI};
    private static final int[] TO = {R.id.Text1, R.id.Text2, R.id.Image1};
    private TimerTask timerTask;
    private Handler handler = new Handler();
    Timer timer = new Timer();
    private ChildEventListener fbListener;

    private static final String[] PROJECTION = {
            Phone._ID,  // mora biti _ID za simplecursoradapter
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.PHOTO_THUMBNAIL_URI
    };

    // TODO: use try-catch

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        Firebase.setAndroidContext(this);
        firebaseInit();

        //SP.registerOnSharedPreferenceChangeListener(listener);

        mAdapter = new SimpleCursorAdapter(this, R.layout.activity_contacts_row, null, FROM, TO, 0);
        mDB = new SQLController(this);
        mDB.open();

        ListView lv = (ListView) findViewById(R.id.ListView1);
        lv.setAdapter(mAdapter);
        getLoaderManager().initLoader(0, null, this);

        final SimpleCursorAdapter.ViewBinder binder = new SimpleCursorAdapter.ViewBinder() {
            @Override
            //prolazi kroz svaki view i njegov redak kursora (jedan redak sadržava više view-ova)
            public boolean setViewValue(View view, Cursor cSQL, int columnIndex) {
                Cursor fbSQL = mDB.fetch();
                String contact_cSQL = cSQL.getString(cSQL.getColumnIndex(Phone.CONTACT_ID));
                // currentRow - da prođe kroz samo prvi view (textview) za svaki redak
                if (currentRow == null || !currentRow.equals(contact_cSQL)) {
                    currentRow = contact_cSQL;
                    // ako je mDB prazna, mijenja boju svih redaka u bijelu
                    if (fbSQL.isAfterLast()) {
                        if (view.getParent().getParent() instanceof RelativeLayout) {
                            ((RelativeLayout) view.getParent().getParent()).setBackgroundColor(Color.WHITE);
                        }
                    } else {
                        for (fbSQL.moveToFirst(); !fbSQL.isAfterLast(); fbSQL.moveToNext()) {
                            String contact_fbSQL = fbSQL.getString(fbSQL.getColumnIndex("_id"));
                            if (contact_cSQL.equals(contact_fbSQL)) {
                                // uspoređuje dva kursora, ako se podudaraju mijenja boju u zelenu
                                // TODO: promijeniti hardcoded parent view, zatvoriti kusore
                                if (view.getParent().getParent() instanceof RelativeLayout) {
                                    ((RelativeLayout) view.getParent().getParent()).setBackgroundColor(Color.GREEN);
                                    break;
                                }
                            } else {
                                if (view.getParent().getParent() instanceof RelativeLayout) {
                                    ((RelativeLayout) view.getParent().getParent()).setBackgroundColor(Color.GRAY);
                                }
                            }
                        }
                    }
                }
                return false;
            }
        };
        mAdapter.setViewBinder(binder);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d("HERE", String.valueOf(position));
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Cursor cSQL = mAdapter.getCursor();
                cSQL.moveToPosition(position);

                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, cSQL.getString(cSQL.getColumnIndex(Phone.CONTACT_ID)));
                intent.setData(uri);
                startActivity(intent);
            }
        });

        //fbListener = addUserRootEventListener();
        initializeTimerTask();
        timer.schedule(timerTask, 0, 10000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_contacts, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_logout) {
            Intent intent = new Intent(ContactsActivity.this, MainActivity.class);
            setResult(Activity.RESULT_OK);
            startActivity(intent);
            //this.finish();
            return true;
        }
        if (id == R.id.action_sync) {
            firebaseWriteToContacts();
            checkCSQLContent();
            mAdapter.notifyDataSetChanged();    // osvježava listview da usporedi cSQL podatke sa novim fbSQL
            return true;
        }
        if (id == R.id.action_backup) {
            firebaseWrite();               // sprema Contacts podatke (cSQL) na FB
            return true;
        }
        if (id == R.id.action_refresh) {
            mAdapter.notifyDataSetChanged();
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            // SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            /* SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    // Implementation
                }
            }; */
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, Phone.CONTENT_URI, PROJECTION, null, null, null);
    }

    @Override  // kada je asinkroni query obavljen
    public void onLoadFinished(Loader<Cursor> loader, final Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // on reset take any old cursor away
        mAdapter.swapCursor(null);
    }

    private void firebaseInit() {
        Firebase mFirebase = new Firebase("https://vivid-heat-1679.firebaseio.com");
        Intent intent = this.getIntent();
        String mUUID = intent.getStringExtra("UUID");
        Log.d("firebaseInit() UUID", mUUID);
        userRoot = mFirebase.child("users/" + mUUID);
    }

    private void firebaseWrite() {
        Cursor cSQL = mAdapter.getCursor();
        Map<String, String> values = new HashMap<>();

        for (cSQL.moveToFirst(); !cSQL.isAfterLast(); cSQL.moveToNext()) {
            values.put(Phone.DISPLAY_NAME_PRIMARY, cSQL.getString(cSQL.getColumnIndex(Phone.DISPLAY_NAME_PRIMARY)));
            values.put(Phone.NUMBER, cSQL.getString(cSQL.getColumnIndex(Phone.NUMBER)));
            values.put(Phone.PHOTO_THUMBNAIL_URI, cSQL.getString(cSQL.getColumnIndex(Phone.PHOTO_THUMBNAIL_URI)));

            // na ključ dodajemo vrijednosti tipa ključ-vrijednost(values)
            userRoot.child(cSQL.getString(cSQL.getColumnIndex(Phone.CONTACT_ID))).setValue(values);
            values.clear();
        }
    }

    void firebaseWriteToContacts() {
        /* TRY THIS
        http://stackoverflow.com/questions/14395011/forcibly-create-new-contact-when-insert-new-raw-contact

        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "");
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "");
        Uri rawContactUri = getContentResolver().insert(ContactsContract.RawContacts.CONTENT_URI, values);
        */
        /* WORKS barely
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int rawContactInsertIndex = ops.size();

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
        ops.add(ContentProviderOperation
                .newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Vikas Patidar") // Name of the person
                .build());
        ops.add(ContentProviderOperation
                //.newUpdate("content://com.android.contacts/data/ID")
                .newInsert(Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        //.withValue(Phone.NAME_RAW_CONTACT_ID, 80)
                .withValue(Phone.NUMBER, "9999999999") // Number of the person
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE).build()); // Type of mobile number
        try
        {
            ContentProviderResult[] res = getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        }
        catch (RemoteException e)
        {
            Log.d("ERROR: ", e.toString());
        }
        catch (OperationApplicationException e)
        {
            Log.d("ERROR: ", e.toString());
        }*/

        /*ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "bla");
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "blabla");
        Uri rawContactUri = getContentResolver().insert(ContactsContract.RawContacts.CONTENT_URI, values);

        values.put(Phone.RAW_CONTACT_ID, 001);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, "            1-800-GOOG-411      ");
        values.put(Phone.TYPE, Phone.TYPE_CUSTOM);
        values.put(Phone.LABEL, "free directory assistance");
        Uri dataUri = getContentResolver().insert(android.provider.ContactsContract.Data.CONTENT_URI, values);
        // cr.delete(ContactsContract.Data.CONTENT_URI,);*/
        //Log.d("URI:", ContentProviderResult[0].toString());
        checkCSQLContent();
    }

    private ChildEventListener addUserRootEventListener () {
        mDB.deleteAll();
        return userRoot.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Log.d("onChildAdded: ", dataSnapshot.getKey());
                HashMap<String, String> temp = new HashMap<>();
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    temp.put(child.getKey(), child.getValue().toString());
                }
                mDB.insertOrReplace(dataSnapshot.getKey(), temp.get("data1"), temp.get("display_name"), temp.get("photo_thumb_uri"));
                mAdapter.notifyDataSetChanged();
                temp.clear();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                Log.d("onChildChanged: ", dataSnapshot.getKey());
                HashMap<String, String> temp2 = new HashMap<>();
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    temp2.put(child.getKey(), child.getValue().toString());
                }
                mDB.insertOrReplace(dataSnapshot.getKey(), temp2.get("data1"), temp2.get("display_name"), temp2.get("photo_thumb_uri"));
                mAdapter.notifyDataSetChanged();
                temp2.clear();
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                Log.d("onChildRemoved: ", dataSnapshot.getKey());
                mDB.delete(dataSnapshot.getKey());
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    private void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        new updateChildListener().execute();
                    }
                });
            }
        };
    }

    class updateChildListener extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... params) {
            if(fbListener != null) {
                userRoot.removeEventListener(fbListener);
                fbListener = null;
                Log.d("doInBackground: ", "LISTENER REMOVED");
            } else {
                fbListener = addUserRootEventListener();
                Log.d("doInBackground: ", "LISTENER ATTACHED");
            }
            return "ok";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            mAdapter.notifyDataSetChanged();
        }
    }

    void checkFBSQLContent() {
        Cursor fbSQL = mDB.fetch();
        Log.d("checkFBSQLContent: ", "Number of columns: " + String.valueOf(fbSQL.getColumnCount()));
        if (fbSQL.isAfterLast()) {
            Log.d("checkFBSQLContent: ", "TABLE EMPTY");
        } else {
            for (fbSQL.moveToFirst(); !fbSQL.isAfterLast(); fbSQL.moveToNext()) {
                for (int i = 0; i < fbSQL.getColumnCount(); i++) {
                    Log.d(fbSQL.getColumnName(i), "" + fbSQL.getString(i));
                }
                Log.d("------", "------");
            }
        }
    }

    void checkCSQLContent() {
        //Cursor cSQL = mAdapter.getCursor();
        ContentResolver cr = getContentResolver();
        Cursor cSQL = cr.query(Phone.CONTENT_URI, null, null, null, null);
        Log.d("checkCSQLContent: ", "Number of columns: " + String.valueOf(cSQL.getColumnCount()));
        if (cSQL.isAfterLast()) {
            Log.d("checkCSQLContent: ", "TABLE EMPTY");
        } else {
            for (cSQL.moveToFirst(); !cSQL.isAfterLast(); cSQL.moveToNext()) {
                for (int i = 0; i < cSQL.getColumnCount(); i++) {
                    Log.d(cSQL.getColumnName(i), "" + cSQL.getString(i));
                }
                Log.d("------", "------");
            }
        }
    }

}