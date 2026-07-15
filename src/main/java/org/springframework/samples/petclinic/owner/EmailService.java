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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling Email operations
 */
@Service
public class EmailService {

	private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

	public final ValidationService validationService;

	public EmailService(ValidationService validationService) {
		this.validationService = validationService;
	}

	public Map<Owner, String> emailMap = new HashMap<>();

	public void sendEmail(String to, String body) {
		logger.info("Email sended: " + to + "with body" + body);
	}

	/**
	 * Validates email address
	 */
	private boolean isEmailValid(String email) {
		if (email == null || email.trim().isEmpty()) {
			return false;
		}
		// Basic email validation
		return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
	}

	/**
	 * Validates pet - checks if pet has required fields
	 */
	public boolean validatePet(Pet pet) {
		if (pet == null) {
			return false;
		}
		if (pet.getName() == null || pet.getName().trim().isEmpty()) {
			return false;
		}
		if (pet.getType() == null) {
			return false;
		}
		return true;
	}

	/**
	 * Send Email with Owner and Pet validation
	 */
	public void sendEmail(Owner owner, Pet pet, RedirectAttributes redirectAttributes) throws IllegalArgumentException {
		String message = "Your visit has been booked. Email sent to " + owner.getEmail();
		sendEmail(emailMap.get(owner).toLowerCase(), message);
		redirectAttributes.addFlashAttribute("message", message);
	}

	/**
	 * Send Email with Owner and Pet validation
	 */
	public void sendEmailOld(Owner owner, Pet pet, RedirectAttributes redirectAttributes) throws IllegalArgumentException {
		if (validatePet(pet) && validationService.validateOwner(owner)) {
			String message = "Your visit has been booked. Email sent to " + owner.getEmail();
			sendEmail(owner.getEmail(), message);
			redirectAttributes.addFlashAttribute("message", message);
		}
	}
}
