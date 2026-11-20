
import requests
from concurrent.futures import ThreadPoolExecutor
from collections import Counter

BOOKING_URL = "http://localhost:8080/api/bookings"

# Replace with an AVAILABLE ShowSeat ID
SHOW_SEAT_ID = 2

TOTAL_USERS = 10


def book_seat(user_id):
    payload = {
        "showSeatId": SHOW_SEAT_ID,
        "userId": user_id
    }

    try:
        response = requests.post(
            BOOKING_URL,
            json=payload,
            timeout=15
        )

        return user_id, response.status_code, response.text

    except requests.RequestException as e:
        return user_id, "ERROR", str(e)


with ThreadPoolExecutor(max_workers=TOTAL_USERS) as executor:
    results = list(
        executor.map(
            book_seat,
            range(1001, 1001 + TOTAL_USERS)
        )
    )

for user_id, status, body in results:
    print(f"User {user_id}: HTTP {status} - {body}")

counts = Counter(status for _, status, _ in results)

print("\nSummary:")
print(f"Successful bookings: {counts[201]}")
print(f"Conflicts: {counts[409]}")
print(f"Other responses: {TOTAL_USERS - counts[201] - counts[409]}")

assert counts[201] == 1, "Expected exactly one successful booking"
assert counts[409] == TOTAL_USERS - 1, "Expected all other requests to be rejected"
