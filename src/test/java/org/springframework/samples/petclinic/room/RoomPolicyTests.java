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
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;

class RoomPolicyTests {

	@ParameterizedTest
	@CsvSource({ "north,CHECKUP,2026-10-01,true", "south,SURGERY,2026-10-31,true", "centre,CHECKUP,2026-09-30,false",
			"south,CHECKUP,2026-11-01,false" })
	void wildcardPoliciesRespectInclusiveDates(String clinic, RoomPolicy.Service service, LocalDate day,
			boolean matches) {
		RoomPolicy rule = new RoomPolicy("clinic-closure", "ANY", RoomPolicy.Service.ANY, null,
				RoomPolicy.Action.BLOCKED, 500, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "operations",
				"Building unavailable");
		assertThat(rule.matches(clinic, service, day)).isEqualTo(matches);
	}

	@Test
	void oldOwnerSpecificPoliciesDoNotBecomeGlobalRoomRestrictions() {
		RoomPolicyStore store = mock(RoomPolicyStore.class);
		given(store.rules()).willReturn(List
			.of(new RoomPolicy("care-plan", "ANY", RoomPolicy.Service.ANY, 7, RoomPolicy.Action.STAFF_REQUIRED, 900,
					LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31), "clinical-team", "Patient coordination")));
		RoomDecisionService decisions = new RoomDecisionService(store);
		assertThat(decisions.decide("north", RoomPolicy.Service.CHECKUP, LocalDate.of(2026, 10, 1)))
			.isEqualTo(RoomPolicy.Action.AVAILABLE);
	}

	@Test
	void snapshotsWithoutOwnersLoadAndSortByPriorityThenKey() throws IOException {
		String json = """
				[
				  {"key":"z-block", "clinic":"south", "service":"ANY", "action":"BLOCKED", "priority":200,
				   "validFrom":"2026-10-01", "validThrough":"2026-10-31", "source":"operations", "reason":"Closure"},
				  {"key":"default", "clinic":"ANY", "service":"ANY", "action":"BLOCKED", "priority":100,
				   "validFrom":"2026-10-01", "validThrough":"2026-10-31", "source":"operations", "reason":"Closure"},
				  {"key":"a-open", "clinic":"south", "service":"CHECKUP", "action":"AVAILABLE", "priority":200,
				   "validFrom":"2026-10-01", "validThrough":"2026-10-31", "source":"operations", "reason":"Open room"}
				]
				""";
		RoomPolicyStore store = new RoomPolicyStore(new ObjectMapper(),
				new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));
		RoomDecisionService decisions = new RoomDecisionService(store);
		assertThat(store.rules()).extracting(RoomPolicy::key).containsExactly("a-open", "z-block", "default");
		assertThat(decisions.decide("south", RoomPolicy.Service.CHECKUP, LocalDate.of(2026, 10, 1)))
			.isEqualTo(RoomPolicy.Action.AVAILABLE);
		assertThat(decisions.decide("south", RoomPolicy.Service.SURGERY, LocalDate.of(2026, 10, 1)))
			.isEqualTo(RoomPolicy.Action.BLOCKED);
		assertThat(decisions.decide("centre", RoomPolicy.Service.CHECKUP, LocalDate.of(2026, 11, 1)))
			.isEqualTo(RoomPolicy.Action.AVAILABLE);
	}

}
