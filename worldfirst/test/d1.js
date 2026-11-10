// A stand-in for Cloudflare D1 over Node's built-in SQLite (node:sqlite), for the tests: same SQL engine, the parts of
// the D1 API the Worker uses (prepare, bind, first, all, run, batch).
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";

export function createD1(schemaPath) {
  const db = new DatabaseSync(":memory:");
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
  };
}
