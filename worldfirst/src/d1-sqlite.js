// D1 (the database Cloudflare gives the Worker) over Node's built-in SQLite (node:sqlite), for running the service on
// your own machine (src/server.js, the Docker image) and for the tests: same SQL engine, the parts of the D1 API the
// Worker uses (prepare, bind, first, all, run, batch).
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";

/**
 * @param schemaPath schema.sql (every statement is CREATE ... IF NOT EXISTS, so applying it at every start is safe)
 * @param file       the database file, or ":memory:"
 */
export function createD1(schemaPath, file = ":memory:") {
  const db = new DatabaseSync(file);
  if (file !== ":memory:") {
    // Safer against a power cut, and readers don't block the writer
    db.exec("PRAGMA journal_mode = WAL");
  }
  db.exec(readFileSync(schemaPath, "utf8"));

  class Statement {
    constructor(sql, args = []) {
      this.sql = sql;
      this.args = args;
    }
    bind(...args) {
      return new Statement(this.sql, args);
    }
    rows() {
      return db.prepare(this.sql).all(...this.args).map((row) => ({ ...row }));
    }
    async first(column) {
      const row = this.rows()[0] ?? null;
      return column && row ? row[column] : row;
    }
    async all() {
      return { results: this.rows(), success: true, meta: {} };
    }
    async run() {
      return { results: this.rows(), success: true, meta: {} };
    }
  }

  return {
    prepare: (sql) => new Statement(sql),
    // D1 runs a batch as one transaction
    async batch(statements) {
      db.exec("BEGIN");
      try {
        const results = statements.map((statement) => ({ results: statement.rows(), success: true, meta: {} }));
        db.exec("COMMIT");
        return results;
      } catch (error) {
        db.exec("ROLLBACK");
        throw error;
      }
    },
    close: () => db.close(),
  };
}
