package hr.fesb.flaresync;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBhelper extends SQLiteOpenHelper {

    // Table Name
    public static final String TABLE_NAME = "contacts";

    // Table columns
    public static final String _ID = "_id";
    public static final String DISPLAY_NAME_PRIMARY = "name";
    public static final String NUMBER = "number";
    public static final String PHOTO_THUMBNAIL_URI = "photo_uri";

    // Database Information
    static final String DB_NAME = "contacts.db";

    // database version, change after each modification to DBHelper to recreate the table
    static final int DB_VERSION = 4;

    // Creating table query
    private static final String CREATE_TABLE = "create table " + TABLE_NAME + "(" + _ID
            + " INTEGER PRIMARY KEY, " + DISPLAY_NAME_PRIMARY + " TEXT, " + NUMBER + " TEXT, " + PHOTO_THUMBNAIL_URI + " TEXT);";
    //" INTEGER PRIMARY KEY AUTOINCREMENT, "

    public DBhelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    // called once on a new table version
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d("DBhelper", "NEW TABLE VERSION: " + newVersion);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}