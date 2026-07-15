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

import org.springframework.stereotype.Service;

/**
 * Service for common validation operations
 */
@Service
public class ValidationService {

	/**
	 * Validates phone based on configured version
	 */
	private boolean validatePhone(String telephone) {
		if (telephone == null || telephone.trim().isEmpty()) {
			return false;
		}
		String cleaned = telephone.replaceAll("[^0-9]", "");
		return cleaned.length() >= 10;
	}

	/**
	 * Validates owner - checks if owner has required fields
	 */
	public boolean validateOwner(Owner owner) {
		if (owner == null) {
			return false;
		}
		boolean firstNameValid = !(owner.getFirstName() == null || owner.getFirstName().trim().isEmpty());
		boolean lastNameValid = !(owner.getLastName() == null || owner.getLastName().trim().isEmpty());
		boolean phoneValid = validatePhone(owner.getTelephone());
		return firstNameValid && lastNameValid && phoneValid;
	}

}
