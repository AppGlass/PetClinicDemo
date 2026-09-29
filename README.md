<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/appglass-logo_dark.svg">
    <img src=".github/appglass-logo.svg" alt="AppGlass" width="128">
  </picture>
</p>

<h1 align="center">AppGlass Demo</h1>

<p align="center">
  Demo project for <a href="https://appglass.org"><b>AppGlass</b></a> —
  debug a live application right where it runs.
</p>

## Room booking game

A veterinarian cannot book an examination room at North even though the clinic schedule shows it free.

### Run

With the private DebuggingLab checkout, run from its root:

```bash
./control-plane/scripts/bootstrap_petclinic.sh ../PetClinicDemo
```

Point it at a PetClinic checkout on the `game` branch.
The launcher copies the private demo data outside the checkout; add `--reset` before the checkout path to restore the incident.

For a standalone checkout, save the supplied **booking-rules.json** in the project or Downloads folder,
then run:

```bash
./run-game.sh
```

Requires JDK 17 or newer; the launcher builds and starts the app at [localhost:8080](http://localhost:8080).
Use `./run-game.sh --help` for an explicit file path or additional launch options.

### Reproduce in the UI

1. Open **Home → Rooms** and select **North**, **October 1, 2026**.
2. Click **Clinic schedule**.
   North’s **Room 1 · Examination** has white **Free** slots at **09:00, 10:00, 11:00**;
   **Room 2 · Surgery** shows red **Maintenance**.
3. Click **Find available rooms**.
   The same clinic and date incorrectly return **“No slots available.”**

Select **South** to try booking: click a room/time in the results, then check its blue **Booked** slot in the schedule.
After the fix, North’s three examination slots can be booked; its surgery room stays unavailable.
See [room booking](docs/rooms.md) for how the schedule and the booking rules work together.
