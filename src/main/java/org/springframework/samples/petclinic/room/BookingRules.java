package org.springframework.samples.petclinic.room;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The rules saying which rooms may be booked online and when, read once at startup from
 * the file {@code petclinic.booking-rules} points at; without that property, the
 * checked-in {@code rooms/booking-rules.json}.
 */
@Component
class BookingRules {

	private final List<BookingRule> rules;

	BookingRules(ObjectMapper mapper,
			@Value("${petclinic.booking-rules:classpath:rooms/booking-rules.json}") Resource resource)
			throws IOException {
		try (var input = resource.getInputStream()) {
			List<BookingRule> loaded = mapper.readValue(input, new TypeReference<>() {
			});
			this.rules = loaded.stream()
				.sorted(Comparator.comparingInt(BookingRule::priority).reversed().thenComparing(BookingRule::key))
				.toList();
		}
	}

	List<BookingRule> all() {
		return this.rules;
	}

	/**
	 * What the highest-priority rule covering the room on that day says; with no such
	 * rule, the room may be booked.
	 */
	BookingRule.Action actionFor(String clinic, Room room, LocalDate day) {
		for (BookingRule rule : this.rules) {
			if (rule.covers(clinic, room, day)) {
				return rule.action();
			}
		}
		return BookingRule.Action.AVAILABLE;
	}

	boolean allowBooking(String clinic, Room room, LocalDate day) {
		return actionFor(clinic, room, day) == BookingRule.Action.AVAILABLE;
	}

}
