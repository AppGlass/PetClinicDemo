# Room booking

**Home → Rooms** opens the room search.
Choose North, South or Centre and a date, then click **Find available rooms**.
Each result names the room and its one-hour slot; clicking it books that slot.
The confirmation, the search results and the **Clinic schedule** all show the booking.
Room bookings have nothing to do with pet owners or visits.

`GET /rooms` and `GET /rooms/availability` take `clinic` and an ISO `date`.
`POST /rooms/book` takes `clinic`, `room` (`EXAMINATION` or `SURGERY`), `date` and `time`.
The server checks availability again, and a database constraint stops two bookings of the same room and time.
Bookings are stored in `room_bookings`; the default H2 database forgets them on restart.

**Clinic schedule** opens `GET /rooms/schedule` for the selected clinic and date:
both rooms of every clinic, hour by hour.
Free slots are white, bookings blue, maintenance red and closed hours gray.
Working hours are 08:00–17:00 every day.
The sample bookings in `rooms/room-bookings.json` repeat daily; real bookings affect only their clinic, room and day.

## Two inputs

The Rooms page combines two files:

- The **maintenance calendar**, `rooms/maintenance-calendar.json`, is part of the application.
  Each entry shuts the listed rooms of a clinic between two dates and shows them as *Maintenance* or *Closed*.
  It paints the schedule grid.
- The **booking rules** say which rooms may be booked online, and when.
  The application reads `rooms/booking-rules.json` unless `petclinic.booking-rules`
  (or the environment variable `PETCLINIC_BOOKING_RULES`) points at another file,
  for example one the clinic's operations team maintains.

A search offers a slot only when the schedule shows it free *and* the booking rules allow the room.
A rule can never make an occupied or closed room bookable.

## Booking rules

Each rule has a `key`, a `clinic`, the `rooms` it covers (`EXAMINATION`, `SURGERY` or both),
an `action`, a `priority`, an inclusive date window (`validFrom`, `validThrough`),
the `source` that published it and a `reason`.

`AVAILABLE` allows online booking; `BLOCKED` and `STAFF_REQUIRED` hide the room from the search.
For a room and day, the rule with the highest priority (then the alphabetically first key) decides;
with no matching rule the room may be booked.

## Changing a rule

Edit the file the application was started with and restart it: the rules are read once at startup.
Fix the rule itself rather than swapping in the checked-in default, which knows nothing about the clinic's real closures.
