package org.springframework.samples.petclinic.room;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface RoomBookingRepository extends JpaRepository<RoomBooking, Integer> {

	List<RoomBooking> findByDay(LocalDate day);

}
