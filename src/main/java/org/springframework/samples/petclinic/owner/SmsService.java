/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for handling SMS operations
 */
@Service
public class SmsService {

	private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

	// Global option: 1 for Version 1 phone checking, 2 for Version 2 phone checking
	private static final int PHONE_CHECK_VERSION = 1;

	private final ValidationService validationService;

	public SmsService(ValidationService validationService) {
		this.validationService = validationService;
	}

	/**
	 * Version 1: Basic phone validation - checks if phone exists and has minimum length
	 */
	private boolean isPhoneValidV1(String telephone) {
		if (telephone == null || telephone.trim().isEmpty()) {
			return false;
		}
		String cleaned = telephone.replaceAll("[^0-9]", "");
		return cleaned.length() >= 10;
	}

	/**
	 * Version 2: Advanced phone validation - checks format and validates area code
	 */
	private boolean isPhoneValidV2(String telephone) {
		if (telephone == null || telephone.trim().isEmpty()) {
			return false;
		}
		String cleaned = telephone.replaceAll("[^0-9]", "");
		if (cleaned.length() < 10) {
			return false;
		}
		// Check if area code is valid (not starting with 0 or 1)
		if (cleaned.length() >= 3) {
			char firstDigit = cleaned.charAt(0);
			if (firstDigit == '0' || firstDigit == '1') {
				return false;
			}
		}
		return true;
	}

	/**
	 * Validates phone based on configured version
	 */
	private boolean validatePhone(String telephone) {
		if (PHONE_CHECK_VERSION == 1) {
			return isPhoneValidV1(telephone);
		}
		else if (PHONE_CHECK_VERSION == 2) {
			return isPhoneValidV2(telephone);
		}
		return false;
	}

	/**
	 * Send SMS with Owner and Pet validation
	 */
	public void sendSms(Owner owner, Pet pet) {
		// Get current trace ID from OpenTelemetry
		String traceId = Span.current().getSpanContext().getTraceId();

		// Validate and send SMS
		if (validationService.validateOwner(owner) && validatePet(pet)) {
			logger.info("Sending SMS to number: " + owner.getTelephone() + " [traceId=" + traceId + "]");
		}
	}

	/**
	 * Validates pet - checks if pet has required fields
	 */
	private boolean validatePet(Pet pet) {
		if (pet.getName() == null || pet.getName().trim().isEmpty()) {
			return false;
		}
		if (pet.getType() == null) {
			return false;
		}
		return true;
	}

}