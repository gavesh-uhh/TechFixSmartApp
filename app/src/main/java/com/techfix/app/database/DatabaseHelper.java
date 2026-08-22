package com.techfix.app.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Single SQLite connection holder for all DAOs. */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "techfix.db";
    private static final int DB_VERSION = 4;

    private static volatile DatabaseHelper instance;

    public static DatabaseHelper getInstance(Context context) {
        if (instance == null) instance = new DatabaseHelper(context.getApplicationContext());
        return instance;
    }

    private DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE branches(id INTEGER PRIMARY KEY,name TEXT,city TEXT,latitude REAL,longitude REAL)");
        db.execSQL("CREATE TABLE services(id INTEGER PRIMARY KEY,name TEXT,category TEXT,price REAL,requiredPart TEXT)");
        db.execSQL("CREATE TABLE technicians(id INTEGER PRIMARY KEY,name TEXT,branch TEXT,skill TEXT,available INTEGER)");
        db.execSQL("CREATE TABLE parts(id INTEGER PRIMARY KEY,name TEXT,quantity INTEGER,branch TEXT)");
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY,title TEXT,service TEXT,imageUri TEXT)");
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,email TEXT UNIQUE,password TEXT,role TEXT DEFAULT 'CUSTOMER')");
        db.execSQL("CREATE TABLE appointments(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER DEFAULT 0,device TEXT,problem TEXT,branch TEXT,status TEXT,service TEXT,price REAL,technician TEXT,payment TEXT,time_slot TEXT DEFAULT '',created_at TEXT DEFAULT '',photo_uri TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE status_history(id INTEGER PRIMARY KEY AUTOINCREMENT,appointment_id INTEGER,status TEXT,updated_at TEXT,note TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT,appointment_id INTEGER,amount REAL,method TEXT,paid_at TEXT)");
        seed(db);
    }

    private void seed(SQLiteDatabase db) {
        db.execSQL("INSERT INTO branches VALUES(1,'Colombo branch','Colombo',6.9271,79.8612),(2,'Galle branch','Galle',6.0329,80.2168)");
        db.execSQL("INSERT INTO services VALUES(1,'Screen replacement','Mobile phone',8500,'Phone display'),(2,'Battery replacement','Mobile phone',4500,'Phone battery'),(3,'Laptop diagnostics','Laptop / computer',3000,''),(4,'Operating system repair','Laptop / computer',6500,'Laptop battery')");
        db.execSQL("INSERT INTO technicians VALUES(1,'Nimal Perera','Colombo branch','Mobile phone',1),(2,'Sahan Silva','Colombo branch','Laptop / computer',1),(3,'Kasun Fernando','Galle branch','Mobile phone',1)");
        db.execSQL("INSERT INTO parts VALUES(1,'Phone display',8,'Colombo branch'),(2,'Laptop battery',4,'Colombo branch'),(3,'Phone battery',5,'Galle branch')");
        db.execSQL("INSERT INTO samples VALUES(1,'iPhone screen restoration','Screen replacement',''),(2,'Laptop performance recovery','Operating system repair','')");
        android.content.ContentValues staff = new android.content.ContentValues();
        staff.put("name", "TechFix Staff"); staff.put("email", "staff@techfix.lk");
        staff.put("password", hash("techfix123")); staff.put("role", "STAFF");
        db.insertWithOnConflict("users", null, staff, SQLiteDatabase.CONFLICT_IGNORE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion >= 4) return;
        db.execSQL("ALTER TABLE users ADD COLUMN role TEXT DEFAULT 'CUSTOMER'");
        db.execSQL("ALTER TABLE appointments ADD COLUMN user_id INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE appointments ADD COLUMN time_slot TEXT DEFAULT ''");
        db.execSQL("ALTER TABLE appointments ADD COLUMN created_at TEXT DEFAULT ''");
        db.execSQL("ALTER TABLE appointments ADD COLUMN photo_uri TEXT DEFAULT ''");
        db.execSQL("CREATE TABLE IF NOT EXISTS status_history(id INTEGER PRIMARY KEY AUTOINCREMENT,appointment_id INTEGER,status TEXT,updated_at TEXT,note TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT,appointment_id INTEGER,amount REAL,method TEXT,paid_at TEXT)");
    }

    public static String hash(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("techfix::" + value).getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) { return value; }
    }

    public static String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date());
    }
}
