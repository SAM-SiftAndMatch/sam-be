"""Execute V47 on populated tables; production upgrades must preserve business data."""
import sqlite3
import unittest
from pathlib import Path


class DemoMigrationSafetyTest(unittest.TestCase):
    def test_v47_preserves_seed_and_followup_transactions(self):
        migration = Path(__file__).resolve().parents[2] / "main/resources/db/migration/V47__Realistic_Demo_Seed_Amounts.sql"
        with sqlite3.connect(":memory:") as db:
            db.executescript("""
                CREATE TABLE jobs(id TEXT,budget_min NUMERIC,budget_max NUMERIC);
                CREATE TABLE proposals(id TEXT,proposed_budget NUMERIC);
                CREATE TABLE contracts(id TEXT,agreed_amount NUMERIC);
                CREATE TABLE payments(contract_id TEXT,amount NUMERIC,payment_type TEXT,environment TEXT);
                CREATE TABLE freelancer_profiles(user_id TEXT,hourly_rate NUMERIC);
            """)
            contract = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
            db.execute("INSERT INTO jobs VALUES(?,?,?)", ("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 1000, 2000))
            db.execute("INSERT INTO jobs VALUES(?,?,?)", ("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 500, 1000))
            db.executemany("INSERT INTO proposals VALUES(?,?)", [
                ("cccccccc-cccc-cccc-cccc-cccccccccccc", 1500),
                ("dddddddd-dddd-dddd-dddd-dddddddddddd", 800)])
            db.execute("INSERT INTO contracts VALUES(?,?)", (contract, 800))
            db.executemany("INSERT INTO payments VALUES(?,?,?,?)", [
                (contract, 800, "CONTRACT_FUND", "LEGACY"),
                (contract, 80, "SECURITY_DEPOSIT", "SANDBOX"),
                (contract, 500000, "MAINTENANCE_FUND", "LIVE")])
            db.executemany("INSERT INTO freelancer_profiles VALUES(?,?)", [
                ("33333333-3333-3333-3333-333333333333", 12),
                ("44444444-4444-4444-4444-444444444444", 987654)])
            tables = ["jobs", "proposals", "contracts", "payments", "freelancer_profiles"]
            before = {table: db.execute(f"SELECT * FROM {table}").fetchall() for table in tables}
            db.executescript(migration.read_text())
            after = {table: db.execute(f"SELECT * FROM {table}").fetchall() for table in tables}
            self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main()
