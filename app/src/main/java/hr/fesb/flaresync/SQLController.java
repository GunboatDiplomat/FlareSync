package hr.fesb.flaresync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class SQLController {

    private DBhelper dbHelper;
    private Context ourcontext;
    private SQLiteDatabase database;

    public SQLController(Context c) {
        ourcontext = c;
    }

    public SQLController open() throws SQLException {
        dbHelper = new DBhelper(ourcontext);
        database = dbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        dbHelper.close();
    }

    public void insert(String id, String number, String name, String photo_uri) {
        ContentValues contentValue = new ContentValues();
        contentValue.put(DBhelper._ID, Integer.parseInt(id));  //id mora biti int
        contentValue.put(DBhelper.NUMBER, number);
        contentValue.put(DBhelper.DISPLAY_NAME_PRIMARY, name);
        contentValue.put(DBhelper.PHOTO_THUMBNAIL_URI, photo_uri);
        database.insert(DBhelper.TABLE_NAME, null, contentValue);
    }

    public void insertOrReplace(String id, String number, String name, String photo_uri) {
        ContentValues contentValue = new ContentValues();
        contentValue.put(DBhelper._ID, Integer.parseInt(id));  //id mora biti int
        contentValue.put(DBhelper.NUMBER, number);
        contentValue.put(DBhelper.DISPLAY_NAME_PRIMARY, name);
        contentValue.put(DBhelper.PHOTO_THUMBNAIL_URI, photo_uri);
        database.insertWithOnConflict(DBhelper.TABLE_NAME, null, contentValue, 5);
        // "INSERT ON CONFLICT REPLACE" ili "INSERT OR REPLACE"
    }

    public Cursor fetch() {
        String[] columns = new String[]{DBhelper._ID, DBhelper.DISPLAY_NAME_PRIMARY, DBhelper.NUMBER, DBhelper.PHOTO_THUMBNAIL_URI};
        Cursor cursor = database.query(DBhelper.TABLE_NAME, columns, null, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;
    }

    /*
    public int update(int _id, String name, String number, String photo_uri) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DBhelper.DISPLAY_NAME_PRIMARY, name);
        contentValues.put(DBhelper.NUMBER, number);
        contentValues.put(DBhelper.PHOTO_THUMBNAIL_URI, photo_uri);
        int i = database.update(DBhelper.TABLE_NAME, contentValues, DBhelper._ID + " = " + _id, null);
        return i;
    }*/

    public void delete(String _id) {
        database.delete(DBhelper.TABLE_NAME, DBhelper._ID + "=" + Integer.parseInt(_id), null);
    }

    public void deleteAll() {
        database.delete(DBhelper.TABLE_NAME, null, null);
    }
}
