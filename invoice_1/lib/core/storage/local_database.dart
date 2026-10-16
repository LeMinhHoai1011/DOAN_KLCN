import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';

class LocalDatabase {
  static final LocalDatabase instance = LocalDatabase._init();
  static Database? _database;

  LocalDatabase._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('smart_invoice.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getApplicationDocumentsDirectory();
    final path = join(dbPath.path, filePath);

    return await openDatabase(
      path,
      version: 1,
      onCreate: _createDB,
    );
  }

  Future _createDB(Database db, int version) async {
    const idType = 'INTEGER PRIMARY KEY AUTOINCREMENT';
    const textType = 'TEXT NOT NULL';
    const intType = 'INTEGER NOT NULL';
    
    await db.execute('''
CREATE TABLE notifications (
  _id $idType,
  server_id $textType,
  title $textType,
  message $textType,
  type $textType,
  document_id $textType,
  is_read $intType,
  created_at $textType
)
''');
  }

  Future<void> clearAllData() async {
    final db = await instance.database;
    await db.delete('notifications');
  }

  Future<void> close() async {
    final db = await instance.database;
    db.close();
  }
}
