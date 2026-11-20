import { openDB } from "idb";

export interface Storage {
  get<T>(key: string): Promise<T | undefined>;
  put<T>(key: string, value: T): Promise<void>;
  del?(key: string): Promise<void>;
}

export class BrowserStorage implements Storage {
  private db = openDB("bitpin-ixh26", 1, {
    upgrade(db) {
      db.createObjectStore("records");
    },
  });
  async get<T>(key: string): Promise<T | undefined> {
    return (await this.db).get("records", key);
  }
  async put<T>(key: string, value: T) {
    await (await this.db).put("records", value, key);
  }
  async del(key: string) {
    await (await this.db).delete("records", key);
  }
}
