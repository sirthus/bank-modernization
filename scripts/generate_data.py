import random
import csv
import os
import sys
from datetime import date, timedelta

# ── Configuration ───────────────────────────────────────────────────

PROFILES = {
    "standard": {
        "customers": 10000,
        "merchants": 500,
        "csv_records": 50000,
        "seed_file": "012_seed_data.sql",
    },
    "prod": {
        "customers": 100000,
        "merchants": 1000,
        "csv_records": 100000,
        "seed_file": "012_seed_data_prod.sql",
    },
}

# ── Name pools ──────────────────────────────────────────────────────

FIRST = [
    "James","Mary","Robert","Patricia","John","Jennifer","Michael","Linda",
    "David","Elizabeth","William","Barbara","Richard","Susan","Joseph","Jessica",
    "Thomas","Sarah","Christopher","Karen","Charles","Lisa","Daniel","Nancy",
    "Matthew","Betty","Anthony","Margaret","Mark","Sandra","Donald","Ashley",
    "Steven","Dorothy","Paul","Kimberly","Andrew","Emily","Joshua","Donna",
    "Kenneth","Michelle","Kevin","Carol","Brian","Amanda","George","Melissa",
    "Timothy","Deborah","Ronald","Stephanie","Edward","Rebecca","Jason","Sharon",
    "Jeffrey","Laura","Ryan","Cynthia","Jacob","Kathleen","Gary","Amy",
    "Nicholas","Angela","Eric","Shirley","Jonathan","Anna","Stephen","Brenda",
    "Larry","Pamela","Justin","Emma","Scott","Nicole","Brandon","Helen",
    "Benjamin","Samantha","Samuel","Katherine","Raymond","Christine","Gregory","Debra",
    "Frank","Rachel","Alexander","Carolyn","Patrick","Janet","Jack","Catherine",
    "Dennis","Maria","Jerry","Heather","Tyler","Diane","Aaron","Ruth",
    "Jose","Virginia","Adam","Brittany","Nathan","Hannah","Henry","Marie",
    "Douglas","Megan","Peter","Andrea","Zachary","Cheryl","Kyle","Jacqueline",
    "Noah","Teresa","Ethan","Gloria","Jeremy","Sara","Walter","Janice",
    "Christian","Jean","Keith","Abigail","Roger","Alice","Terry","Judy",
    "Austin","Sophia","Sean","Grace","Gerald","Denise","Carl","Amber",
    "Harold","Doris","Dylan","Marilyn","Arthur","Danielle","Lawrence","Beverly",
]

LAST = [
    "Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis",
    "Rodriguez","Martinez","Hernandez","Lopez","Gonzalez","Wilson","Anderson",
    "Thomas","Taylor","Moore","Jackson","Martin","Lee","Perez","Thompson",
    "White","Harris","Sanchez","Clark","Ramirez","Lewis","Robinson","Walker",
    "Young","Allen","King","Wright","Scott","Torres","Nguyen","Hill",
    "Flores","Green","Adams","Nelson","Baker","Hall","Rivera","Campbell",
    "Mitchell","Carter","Roberts","Gomez","Phillips","Evans","Turner","Diaz",
    "Parker","Cruz","Edwards","Collins","Reyes","Stewart","Morris","Morales",
    "Murphy","Cook","Rogers","Gutierrez","Ortiz","Morgan","Cooper","Peterson",
    "Bailey","Reed","Kelly","Howard","Ramos","Kim","Cox","Ward",
    "Richardson","Watson","Brooks","Chavez","Wood","James","Bennett","Gray",
    "Mendoza","Ruiz","Hughes","Price","Alvarez","Castillo","Sanders","Patel",
    "Myers","Long","Ross","Foster","Jimenez","Powell","Jenkins","Perry",
    "Russell","Sullivan","Bell","Coleman","Butler","Henderson","Barnes","Gonzales",
    "Fisher","Vasquez","Simmons","Griffin","McDaniel","Kennedy","Wells","Spencer",
    "Stone","Fox","Hicks","Hawkins","Boyd","Mason","Barrett","Olson",
]

MERCHANT_CATEGORIES = {
    "groceries": ["H-E-B","Kroger","Whole Foods","Trader Joes","Safeway","Aldi",
                   "Publix","Wegmans","Food Lion","Sprouts","Costco","Sams Club",
                   "WinCo","Piggly Wiggly","Meijer"],
    "fuel": ["Shell","Chevron","ExxonMobil","BP","Valero","Phillips 66",
             "Marathon","Sinclair","QuikTrip","Buc-ees","RaceTrac","Wawa",
             "Sheetz","Loves","Pilot"],
    "dining": ["McDonalds","Chick-fil-A","Whataburger","Chipotle","Olive Garden",
               "Applebees","Dennys","IHOP","Panda Express","Subway","Wendys",
               "Taco Bell","Panera Bread","Five Guys","Cracker Barrel"],
    "retail": ["Walmart","Target","Amazon","Best Buy","Home Depot","Lowes",
               "Nordstrom","Macys","TJ Maxx","Ross","Dollar General","Dollar Tree",
               "Kohls","JCPenney","Marshalls"],
    "utilities": ["AT&T","Verizon","T-Mobile","Comcast","Spectrum","Duke Energy",
                  "PG&E","ConEdison","National Grid","Dominion Energy","Entergy",
                  "CenterPoint","Austin Energy","ERCOT","TXU Energy"],
    "healthcare": ["CVS Pharmacy","Walgreens","Express Scripts","UnitedHealth",
                   "Aetna","Cigna","BlueCross","Quest Diagnostics","LabCorp",
                   "Minute Clinic","GoodRx","OptumRx","Humana","Anthem","Kaiser"],
    "entertainment": ["Netflix","Spotify","Disney Plus","AMC Theatres","Regal Cinemas",
                      "Apple Music","YouTube Premium","Hulu","HBO Max","Xbox",
                      "PlayStation","Steam","Audible","Kindle","Ticketmaster"],
    "transportation": ["Uber","Lyft","Southwest Airlines","American Airlines",
                       "United Airlines","Delta Airlines","Enterprise","Hertz",
                       "Avis","Amtrak","Greyhound","DART","Capital Metro","Via","Bolt"],
}

# ── Generate functions ──────────────────────────────────────────────

def generate_customers(count, seed=42):
    random.seed(seed)
    customers = []
    used_emails = set()

    for i in range(count):
        cid = 1001 + i
        first = random.choice(FIRST)
        last = random.choice(LAST)
        full_name = f"{first} {last}"

        base_email = f"{first.lower()}.{last.lower()}"
        email = f"{base_email}@example.com"
        counter = 1
        while email in used_emails:
            email = f"{base_email}{counter}@example.com"
            counter += 1
        used_emails.add(email)

        phone = f"555-{random.randint(1000, 9999)}"
        customers.append((cid, full_name, email, phone))

    return customers


def generate_accounts(customers):
    accounts = []
    account_id = 2001

    for cid, _, _, _ in customers:
        accounts.append((account_id, cid, "checking", "active", 0))
        account_id += 1

        if random.random() < 0.6:
            accounts.append((account_id, cid, "savings", "active", 0))
            account_id += 1

        if random.random() < 0.4:
            limit = random.choice([100000, 250000, 500000, 750000, 1000000])
            accounts.append((account_id, cid, "credit", "active", limit))
            account_id += 1

    return accounts


def generate_merchants(count):
    all_merchants = []
    for cat, names in MERCHANT_CATEGORIES.items():
        for name in names:
            all_merchants.append((name, cat))

    while len(all_merchants) < count:
        cat = random.choice(list(MERCHANT_CATEGORIES.keys()))
        base = random.choice(MERCHANT_CATEGORIES[cat])
        num = random.randint(100, 999)
        all_merchants.append((f"{base} #{num}", cat))

    random.shuffle(all_merchants)
    all_merchants = all_merchants[:count]

    merchants = []
    merchant_id = 3001
    for name, cat in all_merchants:
        merchants.append((merchant_id, name, cat))
        merchant_id += 1

    return merchants


def write_seed_sql(customers, accounts, merchants, output_path, label):
    print(f"  Writing {label} seed SQL ({len(customers):,} customers)...")

    with open(output_path, "w") as f:
        f.write(f"-- {os.path.basename(output_path)}\n")
        f.write(f"-- Generated seed data: {len(customers):,} customers, "
                f"{len(accounts):,} accounts, {len(merchants):,} merchants\n\n")

        for batch_start in range(0, len(customers), 500):
            batch = customers[batch_start:batch_start + 500]
            f.write("INSERT INTO bank.customers (customer_id, full_name, email, phone, created_at)\nVALUES\n")
            lines = []
            for cid, name, email, phone in batch:
                name_esc = name.replace("'", "''")
                lines.append(f"    ({cid}, '{name_esc}', '{email}', '{phone}', now())")
            f.write(",\n".join(lines) + ";\n\n")

        for batch_start in range(0, len(accounts), 500):
            batch = accounts[batch_start:batch_start + 500]
            f.write("INSERT INTO bank.accounts (account_id, customer_id, account_type, status, opened_at, credit_limit_cents)\nVALUES\n")
            lines = []
            for aid, cid, atype, status, limit in batch:
                lines.append(f"    ({aid}, {cid}, '{atype}', '{status}', CURRENT_DATE, {limit})")
            f.write(",\n".join(lines) + ";\n\n")

        for batch_start in range(0, len(merchants), 500):
            batch = merchants[batch_start:batch_start + 500]
            f.write("INSERT INTO bank.merchants (merchant_id, name, category, created_at)\nVALUES\n")
            lines = []
            for mid, name, cat in batch:
                name_esc = name.replace("'", "''")
                lines.append(f"    ({mid}, '{name_esc}', '{cat}', now())")
            f.write(",\n".join(lines) + ";\n\n")


def generate_batch_csv(account_ids, merchant_ids, filename, num_records, bad_pct, start_date, output_dir):
    path = os.path.join(output_dir, filename)
    bad_count = 0

    with open(path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["account_id", "merchant_id", "direction", "amount_cents", "txn_date"])

        for i in range(num_records):
            txn_date = start_date + timedelta(days=random.randint(0, 6))
            is_bad = random.random() < bad_pct

            if is_bad:
                bad_count += 1
                bad_type = random.choice(["negative_amount", "zero_amount", "bad_direction"])

                if bad_type == "negative_amount":
                    acct = random.choice(account_ids)
                    mid = random.choice(merchant_ids)
                    writer.writerow([acct, mid, "D", -random.randint(1, 10000), txn_date])
                elif bad_type == "zero_amount":
                    acct = random.choice(account_ids)
                    writer.writerow([acct, "", "C", 0, txn_date])
                elif bad_type == "bad_direction":
                    acct = random.choice(account_ids)
                    mid = random.choice(merchant_ids)
                    writer.writerow([acct, mid, random.choice(["X", "Z", "B"]),
                                     random.randint(100, 50000), txn_date])
            else:
                is_credit = random.random() < 0.2

                if is_credit:
                    acct = random.choice(account_ids)
                    amount = random.choice([
                        random.randint(50000, 500000),
                        random.randint(1000, 25000),
                        random.randint(100, 5000),
                    ])
                    writer.writerow([acct, "", "C", amount, txn_date])
                else:
                    acct = random.choice(account_ids)
                    mid = random.choice(merchant_ids)
                    amount = random.randint(100, 75000)
                    writer.writerow([acct, mid, "D", amount, txn_date])

    print(f"  {filename}: {num_records:,} records ({bad_count:,} bad, {num_records - bad_count:,} good)")


# ── Main ────────────────────────────────────────────────────────────

def main():
    profile = sys.argv[1] if len(sys.argv) > 1 else "all"

    if profile not in ("standard", "prod", "all"):
        print(f"Usage: python generate_data.py [standard|prod|all]")
        print(f"  standard  - 10k customers, 500 merchants")
        print(f"  prod      - 100k customers, 1000 merchants")
        print(f"  all       - generate both (default)")
        sys.exit(1)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    sql_dir = os.path.join(script_dir, "..", "sql")
    csv_dir = os.path.join(script_dir, "..", "sample-data")
    resources_dir = os.path.join(script_dir, "..", "app", "src", "main", "resources")
    os.makedirs(sql_dir, exist_ok=True)
    os.makedirs(csv_dir, exist_ok=True)
    os.makedirs(resources_dir, exist_ok=True)

    profiles_to_run = [profile] if profile != "all" else ["standard", "prod"]

    for p in profiles_to_run:
        cfg = PROFILES[p]
        print(f"\n=== Generating {p} dataset ===")

        customers = generate_customers(cfg["customers"], seed=42 if p == "standard" else 99)
        accounts = generate_accounts(customers)
        merchants = generate_merchants(cfg["merchants"])

        print(f"  Customers: {len(customers):,}")
        print(f"  Accounts:  {len(accounts):,}")
        print(f"  Merchants: {len(merchants):,}")

        write_seed_sql(customers, accounts, merchants,
                       os.path.join(sql_dir, cfg["seed_file"]), p)

        account_ids = [a[0] for a in accounts]
        merchant_ids = [m[0] for m in merchants]

        if p == "standard":
            print(f"\n  Generating standard inbound CSV files...")
            generate_batch_csv(account_ids, merchant_ids,
                               "ach_20250310.csv", cfg["csv_records"], 0.02,
                               date(2025, 3, 10), csv_dir)
            generate_batch_csv(account_ids, merchant_ids,
                               "ach_20250317.csv", cfg["csv_records"], 0.05,
                               date(2025, 3, 17), csv_dir)
            generate_batch_csv(account_ids, merchant_ids,
                               "ach_20250324.csv", cfg["csv_records"], 0.08,
                               date(2025, 3, 24), csv_dir)

            for f in ["ach_20250310.csv", "ach_20250317.csv", "ach_20250324.csv"]:
                src = os.path.join(csv_dir, f)
                dst = os.path.join(resources_dir, f)
                with open(src, "r") as s, open(dst, "w") as d:
                    d.write(s.read())

        if p == "prod":
            print(f"\n  Generating prod inbound CSV files...")
            generate_batch_csv(account_ids, merchant_ids,
                               "ach_20250310_prod.csv", cfg["csv_records"], 0.02,
                               date(2025, 3, 10), csv_dir)
            generate_batch_csv(account_ids, merchant_ids,
                               "ach_20250317_prod.csv", cfg["csv_records"], 0.05,
                               date(2025, 3, 17), csv_dir)
            generate_batch_csv(account_ids, merchant_ids,
                               "ach_20250324_prod.csv", cfg["csv_records"], 0.08,
                               date(2025, 3, 24), csv_dir)

            for f in ["ach_20250310_prod.csv", "ach_20250317_prod.csv", "ach_20250324_prod.csv"]:
                src = os.path.join(csv_dir, f)
                dst = os.path.join(resources_dir, f)
                with open(src, "r") as s, open(dst, "w") as d:
                    d.write(s.read())

    print("\nDone!")


if __name__ == "__main__":
    main()
