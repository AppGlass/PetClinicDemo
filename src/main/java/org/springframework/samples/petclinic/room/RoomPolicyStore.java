package org.springframework.samples.petclinic.room;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class RoomPolicyStore {

	private final List<RoomPolicy> rules;

	RoomPolicyStore(ObjectMapper mapper,
			@Value("${petclinic.appointment-policy:classpath:rooms/development.json}") Resource resource)
			throws IOException {
		try (var input = resource.getInputStream()) {
			List<RoomPolicy> loaded = mapper.readValue(input, new TypeReference<>() {
			});
			this.rules = loaded.stream()
				.sorted(Comparator.comparingInt(RoomPolicy::priority).reversed().thenComparing(RoomPolicy::key))
				.toList();
		}
	}

	List<RoomPolicy> rules() {
		return this.rules;
	}

}
