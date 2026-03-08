import random
import csv
import os
from datetime import date, timedelta

random.seed(42)

OUTPUT_DIR = "/mnt/user-data/outputs"
SQL_DIR = os.path.join(OUTPUT_DIR, "sql")
CSV_DIR = os.path.join(OUTPUT_DIR, "sample-data")
os.makedirs(SQL_DIR, exist_ok=True)
os.makedirs(CSV_DIR, exist_ok=True)

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
               "Applebees","Dennys","IHOP","Panda Express","Subway","Wendy",
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

# ── Generate customers ──────────────────────────────────────────────

print("Generating 10,000 customers...")
customers = []
used_emails = set()

for i in range(10000):
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

# ── Generate accounts ───────────────────────────────────────────────

print("Generating ~20,000 accounts...")
accounts = []
account_id = 2001
account_types = ["checking", "savings", "credit"]

for cid, _, _, _ in customers:
    # Every customer gets checking
    accounts.append((account_id, cid, "checking", "active", 0))
    account_id += 1

    # ~60% get savings
    if random.random() < 0.6:
        accounts.append((account_id, cid, "savings", "active", 0))
        account_id += 1

    # ~40% get credit
    if random.random() < 0.4:
        limit = random.choice([100000, 250000, 500000, 750000, 1000000])
        accounts.append((account_id, cid, "credit", "active", limit))
        account_id += 1

print(f"  Generated {len(accounts)} accounts")

# ── Generate merchants ──────────────────────────────────────────────

print("Generating 500 merchants...")
merchants = []
merchant_id = 3001
all_merchants = []
for cat, names in MERCHANT_CATEGORIES.items():
    for name in names:
        all_merchants.append((name, cat))

# Fill to 500 with numbered variations
while len(all_merchants) < 500:
    cat = random.choice(list(MERCHANT_CATEGORIES.keys()))
    base = random.choice(MERCHANT_CATEGORIES[cat])
    num = random.randint(100, 999)
    all_merchants.append((f"{base} #{num}", cat))

random.shuffle(all_merchants)
all_merchants = all_merchants[:500]

for name, cat in all_merchants:
    merchants.append((merchant_id, name, cat))
    merchant_id += 1

# ── Write seed SQL ──────────────────────────────────────────────────

print("Writing seed SQL...")
seed_path = os.path.join(SQL_DIR, "012_seed_data.sql")

with open(seed_path, "w") as f:
    f.write("-- 012_seed_data.sql\n")
    f.write("-- Generated seed data: 10,000 customers, ~20,000 accounts, 500 merchants\n\n")

    # Customers in batches of 500
    for batch_start in range(0, len(customers), 500):
        batch = customers[batch_start:batch_start + 500]
        f.write("INSERT INTO bank.customers (customer_id, full_name, email, phone, created_at)\nVALUES\n")
        lines = []
        for cid, name, email, phone in batch:
            name_esc = name.replace("'", "''")
            lines.append(f"    ({cid}, '{name_esc}', '{email}', '{phone}', now())")
        f.write(",\n".join(lines) + ";\n\n")

    # Accounts in batches of 500
    for batch_start in range(0, len(accounts), 500):
        batch = accounts[batch_start:batch_start + 500]
        f.write("INSERT INTO bank.accounts (account_id, customer_id, account_type, status, opened_at, credit_limit_cents)\nVALUES\n")
        lines = []
        for aid, cid, atype, status, limit in batch:
            lines.append(f"    ({aid}, {cid}, '{atype}', '{status}', CURRENT_DATE, {limit})")
        f.write(",\n".join(lines) + ";\n\n")

    # Merchants in batches of 500
    f.write("INSERT INTO bank.merchants (merchant_id, name, category, created_at)\nVALUES\n")
    lines = []
    for mid, name, cat in merchants:
        name_esc = name.replace("'", "''")
        lines.append(f"    ({mid}, '{name_esc}', '{cat}', now())")
    f.write(",\n".join(lines) + ";\n\n")

print(f"  Seed SQL written to {seed_path}")

# ── Build lookup lists for CSV generation ───────────────────────────

account_ids = [a[0] for a in accounts]
checking_accounts = [a[0] for a in accounts if a[2] == "checking"]
credit_accounts = [a[0] for a in accounts if a[2] == "credit"]
savings_accounts = [a[0] for a in accounts if a[2] == "savings"]
merchant_ids = [m[0] for m in merchants]

# ── Generate CSV files ──────────────────────────────────────────────

def generate_batch_csv(filename, num_records, bad_pct, start_date):
    """Generate an inbound transaction CSV with a percentage of bad records."""
    path = os.path.join(CSV_DIR, filename)
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
                    writer.writerow([acct, mid, random.choice(["X", "Z", "B"]), random.randint(100, 50000), txn_date])
            else:
                # Normal transaction
                is_credit = random.random() < 0.2  # 20% credits

                if is_credit:
                    acct = random.choice(account_ids)
                    amount = random.choice([
                        random.randint(50000, 500000),   # payroll-sized
                        random.randint(1000, 25000),     # small transfers
                        random.randint(100, 5000),       # refunds
                    ])
                    writer.writerow([acct, "", "C", amount, txn_date])
                else:
                    acct = random.choice(account_ids)
                    mid = random.choice(merchant_ids)
                    amount = random.randint(100, 75000)  # $1 to $750
                    writer.writerow([acct, mid, "D", amount, txn_date])

    print(f"  {filename}: {num_records} records ({bad_count} bad, {num_records - bad_count} good)")
    return path

print("\nGenerating inbound CSV files...")

# Batch 1: clean week, 2% bad
generate_batch_csv("ach_20250310.csv", 50000, 0.02, date(2025, 3, 10))

# Batch 2: slightly messier, 5% bad
generate_batch_csv("ach_20250317.csv", 50000, 0.05, date(2025, 3, 17))

# Batch 3: rough week, 8% bad
generate_batch_csv("ach_20250324.csv", 50000, 0.08, date(2025, 3, 24))

print("\nDone!")
print(f"  Customers: {len(customers)}")
print(f"  Accounts:  {len(accounts)}")
print(f"  Merchants: {len(merchants)}")
print(f"  CSV files: 3 x 50,000 records")
