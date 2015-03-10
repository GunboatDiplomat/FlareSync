package hr.fesb.flaresync;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import com.firebase.client.Firebase;
import java.util.HashMap;
import java.util.Map;

// CHECK https://www.udacity.com/course/viewer#!/c-ud258/l-3372188753/m-3432888624
public class ContactsFragment extends ListFragment implements LoaderCallbacks<Cursor> {
    //LOADERI asinkrono dohvaćaju podatke

    // kada nema interneta pise u lokalnu bazu
    // NoSQL
    // imena stupaca iz DB Contacts
    private static final String[] PROJECTION = {
            Phone._ID, // _ID is always required
            Phone.DISPLAY_NAME_PRIMARY, // that's what we want to display
            Phone.NUMBER
    };

    // and name should be displayed in the text1 textview in item layout
    private static final String[] FROM = { Phone.DISPLAY_NAME_PRIMARY };
    private static final int[] TO = { android.R.id.text1 };

    private CursorAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Firebase.setAndroidContext(getActivity());

        mAdapter = new SimpleCursorAdapter(
                getActivity(),
                android.R.layout.simple_list_item_1,     // layout
                null,           // kursor (prvotno null, popunjen nakon onLoadFinished)
                FROM,           // imena stupaca koje uzima iz kursora
                TO,             // textview
                0               // opcije (0 nema automatskog requery-a, to obavlja Loader)
        );
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // each time we are started use our listadapter
        setListAdapter(mAdapter);
        // and tell loader manager to start loading
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        //onCreateLoader vraća CursorLoader, na temelju njega obavlja se query

        Uri contentUri = Phone.CONTENT_URI;

        return new CursorLoader(
                getActivity(),
                contentUri,     // ime tablice (content://...)
                PROJECTION,     // odabrani stupci (PROJEKCIJA)
                null,           // odabrani retci (SELEKCIJA)
                null,
                null            // sortiranje
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, final Cursor data) {
        // kada je asinkroni query obavljen

        // hash mapa sadrži parove ključ-vrijednost
        Map<String, String> values = new HashMap<>();
        Firebase myFirebaseRef = new Firebase("https://vivid-heat-1679.firebaseio.com");
        Intent intent = getActivity().getIntent();
        String mUUID = intent.getStringExtra("UUID");

        Firebase userRoot = myFirebaseRef.child("users/" + mUUID);

        while (data.moveToNext()) {
            values.put(Phone.DISPLAY_NAME_PRIMARY, data.getString(1));
            values.put(Phone.NUMBER, data.getString(2));

            // na ključ dodajemo vrijednost: ključ-vrijednost(values)
            userRoot.child(data.getString(0)).setValue(values);
            values.clear();
        }
        // postavlja novi cursor u mAdapter
        mAdapter.swapCursor(data);
    }



    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // on reset take any old cursor away
        mAdapter.swapCursor(null);
    }
}