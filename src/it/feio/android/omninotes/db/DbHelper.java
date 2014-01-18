/*******************************************************************************
 * Copyright 2013 Federico Iosue (federico.iosue@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package it.feio.android.omninotes.db;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import it.feio.android.omninotes.R;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.Tag;
import it.feio.android.omninotes.utils.AssetUtils;
import it.feio.android.omninotes.utils.Constants;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

public class DbHelper extends SQLiteOpenHelper {

	// Database name
	private static final String DATABASE_NAME = Constants.DATABASE_NAME;
	// Database version aligned if possible to software version
	private static final int DATABASE_VERSION = 410;
	// Sql query file directory
    private static final String SQL_DIR = "sql" ;
	// Notes table name
	private static final String TABLE_NOTES = "notes";
	// Notes table columns
	private static final String KEY_ID = "note_id";
	public static final String KEY_CREATION = "creation";
	public static final String KEY_LAST_MODIFICATION = "last_modification";
	public static final String KEY_TITLE = "title";
	private static final String KEY_CONTENT = "content";
	private static final String KEY_ARCHIVED = "archived";
	private static final String KEY_ALARM = "alarm";
	private static final String KEY_LATITUDE = "latitude";
	private static final String KEY_LONGITUDE = "longitude";
	private static final String KEY_TAG = "tag_id";
	private static final String KEY_LOCKED = "locked";
	// Attachments table name
	private static final String TABLE_ATTACHMENTS = "attachments";
	// Attachments table columns
	private static final String KEY_ATTACHMENT_ID = "attachment_id"; 
	private static final String KEY_ATTACHMENT_URI = "uri"; 
	private static final String KEY_ATTACHMENT_MIME_TYPE = "mime_type"; 
	private static final String KEY_ATTACHMENT_NOTE_ID = "note_id"; 	

	// Tags table name
	private static final String TABLE_TAGS = "tags";
	// Tags table columns
	private static final String KEY_TAG_ID = "tag_id"; 
	private static final String KEY_TAG_NAME = "name"; 
	private static final String KEY_TAG_DESCRIPTION = "description"; 
	private static final String KEY_TAG_COLOR = "color"; 
	
	// Queries    
    private static final String CREATE_QUERY = "create.sql";
    private static final String UPGRADE_QUERY_PREFIX = "upgrade-";    
    private static final String UPGRADE_QUERY_SUFFIX = ".sql";


	private final Context ctx;
	private final SharedPreferences prefs;

	public DbHelper(Context ctx) {
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		this.ctx = ctx;
		this.prefs = PreferenceManager
				.getDefaultSharedPreferences(ctx);
	}
	
	public String getDatabaseName() {
		return DATABASE_NAME;
	}

	
	// Creating Tables
	@Override
	public void onCreate(SQLiteDatabase db) {
		try {
            Log.i(Constants.TAG, "Database creation");
            execSqlFile(CREATE_QUERY, db);
        } catch( IOException exception ) {
            throw new RuntimeException("Database creation failed", exception);
        }
	}

	
	// Upgrading database
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(Constants.TAG, "Upgrading database version from " + oldVersion + " to " + newVersion );
        try {
            for( String sqlFile : AssetUtils.list(SQL_DIR, ctx.getAssets())) {
                if ( sqlFile.startsWith(UPGRADE_QUERY_PREFIX)) {
                    int fileVersion = Integer.parseInt(sqlFile.substring(UPGRADE_QUERY_PREFIX.length(),  sqlFile.length() - UPGRADE_QUERY_SUFFIX.length())); 
                    if ( fileVersion > oldVersion && fileVersion <= newVersion ) {
                        execSqlFile( sqlFile, db );
                    }
                }
            }
            Log.i(Constants.TAG, "Database upgrade successful");
        } catch( IOException exception ) {
            throw new RuntimeException("Database upgrade failed", exception );
        }
	}
	
	
	protected void execSqlFile(String sqlFile, SQLiteDatabase db ) throws SQLException, IOException {
        Log.i(Constants.TAG, "  exec sql file: {}" + sqlFile );
        for( String sqlInstruction : SqlParser.parseSqlFile( SQL_DIR + "/" + sqlFile, ctx.getAssets())) {
        	Log.v(Constants.TAG, "    sql: {}" + sqlInstruction );
        	try {
        		db.execSQL(sqlInstruction);
        	} catch (Exception e) {
        		Log.e(Constants.TAG, "Error executing command: " + sqlInstruction);
        	}
        }
    }

	
	// Inserting or updating single note
	public Note updateNote(Note note) {
		
		long resNote, resAttachment;
		SQLiteDatabase db = this.getWritableDatabase();
		
		// To ensure note and attachments insertions are atomical and boost performances transaction are used
		db.beginTransaction();

		ContentValues values = new ContentValues();
		values.put(KEY_TITLE, note.getTitle());
		values.put(KEY_CONTENT, note.getContent());
		values.put(KEY_LAST_MODIFICATION, Calendar.getInstance().getTimeInMillis());
		boolean archive = note.isArchived() != null ? note.isArchived() : false;
		values.put(KEY_ARCHIVED, archive);
		values.put(KEY_ALARM, note.getAlarm());
		values.put(KEY_LATITUDE, note.getLatitude());
		values.put(KEY_LONGITUDE, note.getLongitude());
		values.put(KEY_TAG, note.getTag() != null ? note.getTag().getId() : null);
		boolean locked = note.isLocked() != null ? note.isLocked() : false;
		values.put(KEY_LOCKED, locked);

		// Updating row
		if (note.get_id() != 0) {
			values.put(KEY_ID, note.get_id());
			resNote = db.update(TABLE_NOTES, values, KEY_ID + " = ?",
					new String[] { String.valueOf(note.get_id()) });
			// Importing data from csv without existing note in db
			if (resNote == 0) {
				resNote = db.insert(TABLE_NOTES, null, values);
			}
			Log.d(Constants.TAG, "Updated note titled '" + note.getTitle()
					+ "'");
			// Inserting new note
		} else {
			values.put(KEY_CREATION, Calendar.getInstance().getTimeInMillis());
			resNote = db.insert(TABLE_NOTES, null, values);
			Log.d(Constants.TAG, "Saved new note titled '" + note.getTitle()
					+ "' with id: " + resNote);
		}
		
		// Updating attachments
		ContentValues valuesAttachments = new ContentValues();
		ArrayList<Attachment> deletedAttachments = note.getAttachmentsListOld();
		for (Attachment attachment : note.getAttachmentsList()) {
			// Updating attachment
			if (attachment.getId() == 0) {
				valuesAttachments.put(KEY_ATTACHMENT_URI, attachment.getUri().toString());
				valuesAttachments.put(KEY_ATTACHMENT_MIME_TYPE, attachment.getMime_type());
				valuesAttachments.put(KEY_ATTACHMENT_NOTE_ID, (note.get_id() != 0 ? note.get_id() : resNote) );
				resAttachment = db.insert(TABLE_ATTACHMENTS, null, valuesAttachments);
				Log.d(Constants.TAG, "Saved new attachment with uri '"
						+ attachment.getUri().toString() + "' with id: " + resAttachment);
			} else {
				deletedAttachments.remove(attachment);
			}
		}
		// Remove from database deleted attachments
		for (Attachment attachmentDeleted : deletedAttachments) {
			db.delete(TABLE_ATTACHMENTS, KEY_ATTACHMENT_ID + " = ?",
					new String[] { String.valueOf(attachmentDeleted.getId()) });
		}
		
		db.setTransactionSuccessful();
		db.endTransaction();
		
		db.close();

		// Fill the note with correct data before returning it
		note.set_id(note.get_id() != 0 ? note.get_id() : (int)resNote);
		note.setCreation(note.getCreation() != null ? note.getCreation() : values.getAsLong(KEY_CREATION));
		note.setLastModification(values.getAsLong(KEY_LAST_MODIFICATION));	
		
		return note;
	}

	
	
	
	/**
	 * Getting single note
	 * @param id
	 * @return
	 */
	public Note getNote(int id) {
		SQLiteDatabase db = getReadableDatabase();

//		Cursor cursor = db.query(TABLE_NOTES, new String[] { KEY_ID,
//				KEY_CREATION, KEY_LAST_MODIFICATION, KEY_TITLE, KEY_CONTENT,
//				KEY_ARCHIVED, KEY_ALARM, KEY_LATITUDE, KEY_LONGITUDE, KEY_TAG }, KEY_ID + "=?",
//				new String[] { String.valueOf(id) }, null, null, null, null);
		
		String query = "SELECT " + KEY_ID + "," +
				KEY_CREATION + "," + KEY_LAST_MODIFICATION + "," + KEY_TITLE + "," +  KEY_CONTENT + "," + 
				KEY_ARCHIVED + "," +  KEY_ALARM + "," +  KEY_LATITUDE + "," +  KEY_LONGITUDE + "," +  KEY_TAG
				+ " FROM " + TABLE_NOTES + " LEFT JOIN " + TABLE_TAGS + " ON " + KEY_TAG + " = " + KEY_TAG_ID;
		Cursor cursor = db.rawQuery(query, null);
		
		if (cursor != null)
			cursor.moveToFirst();

		Note note = new Note(Integer.parseInt(cursor.getString(0)),
				cursor.getLong(1), cursor.getLong(2), cursor.getString(3),
				cursor.getString(4), cursor.getInt(5), cursor.getString(6),
				cursor.getString(7), cursor.getString(8), getTag(Integer.parseInt(cursor.getString(9)))
				, cursor.getInt(5));
		
		// Add eventual attachments uri
		note.setAttachmentsList(getNoteAttachments(note));
		
		db.close();
		return note;
	}

	
	
	/**
	 * Getting All notes
	 * 
	 * @param checkNavigation
	 *            Tells if navigation status (notes, archived) must be kept in
	 *            consideration or if all notes have to be retrieved
	 * @return Notes list
	 */
	public List<Note> getAllNotes(boolean checkNavigation) {

		// Checking if archived or reminders notes must be shown
		String[] navigationListCodes = ctx.getResources().getStringArray(R.array.navigation_list_codes);
		String navigation = prefs.getString(Constants.PREF_NAVIGATION, navigationListCodes[0]);
		String whereCondition = "";
		boolean notes = navigationListCodes[0].equals(navigation);
		boolean archived = navigationListCodes[1].equals(navigation);
		boolean reminders = navigationListCodes[2].equals(navigation);
		boolean tag = !notes && ! archived && !reminders;		
		if (checkNavigation) {
			whereCondition = notes ? " WHERE " + KEY_ARCHIVED + " != 1 " : whereCondition;			
			whereCondition = archived ? " WHERE " + KEY_ARCHIVED + " = 1 " : whereCondition;
			whereCondition = reminders ? " WHERE " + KEY_ALARM + " != 0 " : whereCondition;
			whereCondition = tag ? " WHERE " + TABLE_NOTES + "." + KEY_TAG + " = " + navigation : whereCondition;
		}		
		return getNotes(whereCondition, true);
	}
	
	
	
	/**
	 * Common method for notes retrieval. It accepts a query to perform and returns matching records.
	 * @param query
	 * @return Notes list
	 */
	private List<Note> getNotes(String whereCondition, boolean order) {
		List<Note> noteList = new ArrayList<Note>();

		// Getting sorting criteria from preferences
		String sort_column = "", sort_order = "";
		if (order) {
			sort_column = prefs.getString(Constants.PREF_SORTING_COLUMN,
					KEY_TITLE);
			sort_order = KEY_TITLE.equals(sort_column) ? " ASC " : " DESC ";
		}
		
		// Generic query to be specialized with conditions passed as parameter
		String query = "SELECT " 
						+ KEY_ID + "," 
						+ KEY_CREATION + "," 
						+ KEY_LAST_MODIFICATION + "," 
						+ KEY_TITLE + "," 
						+ KEY_CONTENT + "," 
						+ KEY_ARCHIVED + "," 
						+ KEY_ALARM + "," 
						+ KEY_LATITUDE + "," 
						+ KEY_LONGITUDE + "," 
						+ KEY_LOCKED + "," 
						+ KEY_TAG + "," 
						+ KEY_TAG_NAME + "," 
						+ KEY_TAG_DESCRIPTION + "," 
						+ KEY_TAG_COLOR 
					+ " FROM " + TABLE_NOTES 
					+ " LEFT JOIN " + TABLE_TAGS + " USING( " + KEY_TAG + ") "						
					+ whereCondition
					+ (order ? " ORDER BY " + sort_column + sort_order : "");

		Log.d(Constants.TAG, "Query: " + query);

		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery(query, null);

		// Looping through all rows and adding to list
		if (cursor.moveToFirst()) {
			do {
				Note note = new Note();
				note.set_id(Integer.parseInt(cursor.getString(0)));
				note.setCreation(cursor.getLong(1));
				note.setLastModification(cursor.getLong(2));
				note.setTitle(cursor.getString(3));
				note.setContent(cursor.getString(4));
				note.setArchived("1".equals(cursor.getString(5)));
				note.setAlarm(cursor.getString(6));		
				note.setLatitude(cursor.getString(7));
				note.setLongitude(cursor.getString(8));
				note.setLocked("1".equals(cursor.getString(9)));
				
				// Set tag
				Tag tag = new Tag(cursor.getInt(10), cursor.getString(11), cursor.getString(12), cursor.getString(13));
				note.setTag(tag);
				
				// Add eventual attachments uri
				note.setAttachmentsList(getNoteAttachments(note));
				
				// Adding note to list
				noteList.add(note);
				
			} while (cursor.moveToNext());
		}

		cursor.close();
		db.close();

		return noteList;
	}
	
	


	/**
	 * Getting notes count
	 * @return
	 */
	public int getNotesCount() {
		String countQuery = "SELECT * FROM " + TABLE_NOTES;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery(countQuery, null);
		cursor.close();
		db.close();
		return cursor.getCount();
	}

	
	
	/**
	 * Deleting single note
	 * @param note
	 */
	public boolean deleteNote(Note note) {		
		int deletedNotes, deletedAttachments;
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		// Delete notes
		deletedNotes= db.delete(TABLE_NOTES, KEY_ID + " = ?",
				new String[] { String.valueOf(note.get_id()) });
		
		// Delete note's attachments
		deletedAttachments = db.delete(TABLE_ATTACHMENTS, KEY_ATTACHMENT_NOTE_ID + " = ?",
				new String[] { String.valueOf(note.get_id()) });
		
		db.close();
		
		// Check on correct and complete deletion
		boolean result = deletedNotes == 1 && deletedAttachments == note.getAttachmentsList().size();
		
		return result;
	}

	
	

	/**
	 * Clears completelly the database
	 */
	public void clear() {
		SQLiteDatabase db = this.getWritableDatabase();
		db.execSQL("DELETE FROM " + TABLE_NOTES);
		db.close();
	}

	
	
	/**
	 * Getting notes matching pattern with title or content text
	 * 
	 * @param checkNavigation
	 *            Tells if navigation status (notes, archived) must be kept in
	 *            consideration or if all notes have to be retrieved
	 * @return Notes list
	 */
	public List<Note> getMatchingNotes(String pattern) {
		// Select All Query
		String whereCondition = " WHERE "
								+ KEY_TITLE + " LIKE '%" + pattern + "%' " + " OR "
								+ KEY_CONTENT + " LIKE '%" + pattern + "%' ";
		return getNotes(whereCondition, true);
	}
	
	
	
	
	
	/**
	 * Search for notes with reminder
	 * @param passed Search also for fired yet reminders
	 * @return Notes list
	 */
	public List<Note> getNotesWithReminder(boolean passed) {
		// Select query
		String whereCondition = " WHERE " + KEY_ALARM 
								+ (passed ? " IS NOT NULL" : " >= " + Calendar.getInstance().getTimeInMillis());
		return getNotes(whereCondition, false);
	}


	
	/**
	 * Retrieves all attachments related to specifi note
	 * @param note
	 * @return List of attachments
	 */
	public ArrayList<Attachment> getNoteAttachments(Note note) {
		
		ArrayList<Attachment> attachmentsList = new ArrayList<Attachment>();
		String sql = "SELECT " 
						+ KEY_ATTACHMENT_ID + "," 
						+ KEY_ATTACHMENT_URI + ","
						+ KEY_ATTACHMENT_MIME_TYPE
					+ " FROM " + TABLE_ATTACHMENTS 
					+ " WHERE " + KEY_ATTACHMENT_NOTE_ID + " = " + note.get_id();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery(sql, null);

		// Looping through all rows and adding to list
		if (cursor.moveToFirst()) {
			do {
				attachmentsList.add(new Attachment(Integer.valueOf(cursor.getInt(0)), Uri.parse(cursor.getString(1)), cursor.getString(2)));
			} while (cursor.moveToNext());
		}
		return attachmentsList;		
	}
	

	/**
	 * Retrieves tags list from database
	 * @return List of tags
	 */
	public ArrayList<Tag> getTags() {		
		ArrayList<Tag> tagsList = new ArrayList<Tag>();
		String sql = "SELECT " 
						+ KEY_TAG_ID + "," 
						+ KEY_TAG_NAME + ","
						+ KEY_TAG_DESCRIPTION  + ","
						+ KEY_TAG_COLOR
					+ " FROM " + TABLE_TAGS;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery(sql, null);

		// Looping through all rows and adding to list
		if (cursor.moveToFirst()) {
			do {
				tagsList.add(new Tag(Integer.valueOf(cursor.getInt(0)), cursor.getString(1), cursor.getString(2), cursor.getString(3)));
			} while (cursor.moveToNext());
		}
		db.close();
		return tagsList;		
	}

	
	/**
	 * Updates or insert a new a tag
	 * @param tag Tag to be updated or inserted
	 * @return Rows affected or new inserted tag id
	 */
	public long updateTag(Tag tag) {

		long res;
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(KEY_TAG_NAME, tag.getName());
		values.put(KEY_TAG_DESCRIPTION, tag.getDescription());
		values.put(KEY_TAG_COLOR, tag.getColor());

		// Updating row
		if (tag.getId() != null) {
			values.put(KEY_TAG_ID, tag.getId());
			res = db.update(TABLE_TAGS, values, KEY_TAG_ID + " = ?",
					new String[] { String.valueOf(tag.getId()) });
			Log.d(Constants.TAG, "Updated tag titled '" + tag.getName() + "'");
		// Inserting new tag
		} else {
			res = db.insert(TABLE_TAGS, null, values);
			Log.d(Constants.TAG, "Saved new tag titled '" + tag.getName()
					+ "' with id: " + res);
		}
		db.close();
		
		// Returning result
		return res;
	}
	
	
	/**
	 * Deletion of  a tag
	 * @param tag Tag to be deleted
	 * @return Number 1 if tag's record has been deleted, 0 otherwise
	 */
	public long deleteTag(Tag tag) {		
		long deleted;
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		// Un-tag notes associated with this tag
		ContentValues values = new ContentValues();
		values.put(KEY_TAG, "");

		// Updating row
		db.update(TABLE_NOTES, values, KEY_TAG + " = ?",
				new String[] { String.valueOf(tag.getId()) });
		
		// Delete tag
		deleted= db.delete(TABLE_TAGS, KEY_TAG_ID + " = ?",
				new String[] { String.valueOf(tag.getId()) });
		
		db.close();
		return deleted;
	}

	
	/**
	 * Get note TAG
	 * @param id
	 * @return
	 */
	public Tag getTag(Integer id) {		
		Tag tag = null;
		String sql = "SELECT " 
							+ KEY_TAG_ID + "," 
							+ KEY_TAG_NAME + ","
							+ KEY_TAG_DESCRIPTION  + ","
							+ KEY_TAG_COLOR
						+ " FROM " + TABLE_TAGS
						+ " WHERE " + KEY_TAG_ID + " = " + id;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery(sql, null);

		// Looping through all rows and adding to list
		if (cursor.moveToFirst()) {
			tag = new Tag(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3));
		}
		return tag;
	}
	

	
	public int getTaggedCount(Tag tag) {		
		int count = 0;
		String sql = "SELECT COUNT(*)" 
					+ " FROM " + TABLE_NOTES
					+ " WHERE " + KEY_TAG + " = " + tag.getId();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery(sql, null);

		// Looping through all rows and adding to list
		if (cursor.moveToFirst()) {
			count = cursor.getInt(0);
		}
		return count;		
	}
	

	
	/**
	 * Unlocks all notes after security password removal
	 * @return
	 */
	public int unlockAllNotes() {			
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(KEY_LOCKED, "0");
		
		// Updating row
		return db.update(TABLE_NOTES, values, null, new String[] {});		
	}
	
	
	
	 
}
