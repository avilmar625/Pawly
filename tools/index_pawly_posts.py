"""Index public post fields from legacy clients. Dry-run by default; use application default credentials."""
import argparse
import datetime
import re
import unicodedata


def tokens(data):
    fields = [data.get(k, "") for k in ("text", "dogName", "authorName", "category")]
    text = unicodedata.normalize("NFD", " ".join(v for v in fields if isinstance(v, str)).lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return sorted({s[:64] for s in re.findall(r"[^\W_]+", text)})


def main():
    from google.cloud import firestore
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("--project", required=True)
    args.add_argument("--apply", action="store_true")
    args.add_argument("--since", help="Only posts created since this ISO 8601 date and timezone")
    opt = args.parse_args()
    db = firestore.Client(project=opt.project)
    query = db.collection("posts").order_by("createdAt")
    if opt.since:
        since = datetime.datetime.fromisoformat(opt.since.replace("Z", "+00:00"))
        if not since.tzinfo:
            args.error("--since requires a timezone")
        query = query.where(filter=firestore.FieldFilter("createdAt", ">=", since))
    scanned = changed = 0
    cursor = None
    while True:
        page = query.limit(100)
        if cursor:
            page = page.start_after(cursor)
        docs = list(page.stream())
        if not docs:
            break
        for doc in docs:
            scanned += 1
            values = doc.to_dict()
            expected = tokens(values)
            if expected != values.get("searchTokens"):
                if opt.apply:
                    # Updates only the derived index; refuses to recreate a removed post.
                    doc.reference.update({"searchTokens": expected})
                changed += 1
        cursor = docs[-1]
        if len(docs) < 100:
            break
    print(f"Scanned {scanned}. {'Indexed' if opt.apply else 'Would index'} {changed}.")


if __name__ == "__main__":
    main()
