package org.springframework.samples.petclinic.room;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class BookingRulesTests {

	@ParameterizedTest
	@CsvSource({ "north,EXAMINATION,2026-10-01,true", "north,SURGERY,2026-10-31,true",
			"north,EXAMINATION,2026-09-30,false", "north,SURGERY,2026-11-01,false",
			"south,EXAMINATION,2026-10-01,false" })
	void aRuleCoversItsClinicAndRoomsBetweenBothDatesInclusive(String clinic, Room room, LocalDate day,
			boolean covered) {
		BookingRule rule = new BookingRule("north-closed", "north", List.of(Room.EXAMINATION, Room.SURGERY),
				BookingRule.Action.BLOCKED, 500, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "operations",
				"Building unavailable");
		assertThat(rule.covers(clinic, room, day)).isEqualTo(covered);
	}

	@Test
	void aRuleListingOneRoomLeavesTheOtherAlone() {
		BookingRule rule = new BookingRule("north-surgery", "north", List.of(Room.SURGERY), BookingRule.Action.BLOCKED,
				500, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "operations", "Surgery room closed");
		assertThat(rule.covers("north", Room.SURGERY, LocalDate.of(2026, 10, 1))).isTrue();
		assertThat(rule.covers("north", Room.EXAMINATION, LocalDate.of(2026, 10, 1))).isFalse();
	}

	@Test
	void rulesLoadSortedByPriorityThenKeyAndTheFirstCoveringRuleDecides() throws IOException {
		String json = """
				[
				  {"key":"z-block", "clinic":"south", "rooms":["EXAMINATION","SURGERY"], "action":"BLOCKED", "priority":200,
				   "validFrom":"2026-10-01", "validThrough":"2026-10-31", "source":"operations", "reason":"Closed"},
				  {"key":"default", "clinic":"south", "rooms":["EXAMINATION","SURGERY"], "action":"BLOCKED", "priority":100,
				   "validFrom":"2026-10-01", "validThrough":"2026-10-31", "source":"operations", "reason":"Closed"},
				  {"key":"a-open", "clinic":"south", "rooms":["EXAMINATION"], "action":"AVAILABLE", "priority":200,
				   "validFrom":"2026-10-01", "validThrough":"2026-10-31", "source":"operations", "reason":"Open room"}
				]
				""";
		BookingRules rules = new BookingRules(new ObjectMapper(),
				new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));
		assertThat(rules.all()).extracting(BookingRule::key).containsExactly("a-open", "z-block", "default");
		assertThat(rules.actionFor("south", Room.EXAMINATION, LocalDate.of(2026, 10, 1)))
			.isEqualTo(BookingRule.Action.AVAILABLE);
		assertThat(rules.actionFor("south", Room.SURGERY, LocalDate.of(2026, 10, 1)))
			.isEqualTo(BookingRule.Action.BLOCKED);
		assertThat(rules.actionFor("centre", Room.EXAMINATION, LocalDate.of(2026, 11, 1)))
			.isEqualTo(BookingRule.Action.AVAILABLE);
		assertThat(rules.allowBooking("south", Room.SURGERY, LocalDate.of(2026, 10, 1))).isFalse();
	}

}
