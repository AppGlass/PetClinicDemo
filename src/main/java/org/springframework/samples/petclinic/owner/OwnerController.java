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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import io.sentry.ISpan;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Wick Dynex
 */
@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private static final Logger log = LoggerFactory.getLogger(OwnerController.class);

	private final OwnerRepository owners;

	private final SmsService smsService;

	private final EmailService emailService;

	public OwnerController(OwnerRepository owners, SmsService smsService, EmailService emailService) {
		this.owners = owners;
		this.smsService = smsService;
		this.emailService = emailService;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		return ownerId == null ? new Owner()
				: this.owners.findById(ownerId)
					.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId
							+ ". Please ensure the ID is correct " + "and the owner exists in the database."));
	}

	@GetMapping("/owners/new")
	public String initCreationForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in creating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Owner Created");
		return "redirect:/owners/" + owner.getId();
	}

	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	@GetMapping("/owners")
	public String processFindForm(@RequestParam(name = "page", defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {
		// allow parameterless GET request for /owners to return all records
		String lastName = owner.getLastName();
		if (lastName == null) {
			lastName = ""; // empty string signifies broadest possible search
		}

		// find owners by last name
		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, lastName);
		if (ownersResults.isEmpty()) {
			// no owners found
			result.rejectValue("lastName", "notFound", "not found");
			return "owners/findOwners";
		}

		if (ownersResults.getTotalElements() == 1) {
			// 1 owner found
			owner = ownersResults.iterator().next();
			return "redirect:/owners/" + owner.getId();
		}

		// multiple owners found
		return addPaginationModel(page, model, ownersResults);
	}

	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		return owners.findByLastNameStartingWith(lastname, pageable);
	}

	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId,
			RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in updating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		if (!Objects.equals(owner.getId(), ownerId)) {
			result.rejectValue("id", "mismatch", "The owner ID in the form does not match the URL.");
			redirectAttributes.addFlashAttribute("error", "Owner ID mismatch. Please try again.");
			return "redirect:/owners/{ownerId}/edit";
		}

		owner.setId(ownerId);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "Owner Values Updated");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Custom handler for displaying an owner.
	 * @param ownerId the ID of the owner to display
	 * @return a ModelMap with the model attributes for the view
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		mav.addObject(owner);
		return mav;
	}

	/**
	 * Send SMS to all owners in a specific city
	 */
	@PostMapping("/sms/city/{cityName}")
	public String sendSmsToCity(@PathVariable("cityName") String cityName, RedirectAttributes redirectAttributes) {
		List<Owner> cityOwners = this.owners.findByCity(cityName);

		if (cityOwners.isEmpty()) {
			redirectAttributes.addFlashAttribute("message", "No owners found in city: " + cityName);
			return "redirect:/";
		}

		int sentCount = 0;
		for (Owner owner : cityOwners) {
			// Get first pet or null if no pets
			Pet pet = owner.getPets().isEmpty() ? null : owner.getPets().get(0);
			smsService.sendSms(owner, pet);
			sentCount++;
		}

		redirectAttributes.addFlashAttribute("message", "SMS sent to " + sentCount + " owner(s) in " + cityName);
		return "redirect:/";
	}

	private Pet getFirstPet(Owner owner) {
		List<Pet> pets = owner.getPets();
		return pets.isEmpty() ? null : pets.get(0);
	}

	/**
	 * Send SMS to all owners - triggers sending in a separate thread, iterating through
	 * all cities Uses strict mode for validation
	 */
	@PostMapping("/sms/all")
	public String sendSmsToAll(RedirectAttributes redirectAttributes) {
		List<Owner> allOwners = this.owners.findAll();

		if (allOwners.isEmpty()) {
			redirectAttributes.addFlashAttribute("message", "No owners found");
			return "redirect:/";
		}

		// Get distinct cities
		List<String> cities = allOwners.stream()
			.map(Owner::getCity)
			.distinct()
			.sorted(Comparator.reverseOrder())
			.collect(java.util.stream.Collectors.toList());

		// Start sending in a separate thread
		CompletableFuture.runAsync(() -> {
			for (String city : cities) {
				triggerSmsByCityEndpoint(city);
			}
		}).exceptionally(ex -> {
			log.error("Async SMS failed", ex);
			return null;
		});
		;

		redirectAttributes.addFlashAttribute("message",
				"SMS sending started for " + cities.size() + " cities in background");
		return "redirect:/";
	}

	@PostMapping("/emails/process")
	public String processEmails(RedirectAttributes redirectAttributes) {
		List<Owner> allOwners = this.owners.findAll();
		log.info("Email DB processing started");

		if (allOwners.isEmpty()) {
			redirectAttributes.addFlashAttribute("message", "No owners found");
			return "redirect:/";
		}

		int processedCount = 0;
		for (Owner owner : allOwners) {
			Pet pet = getFirstPet(owner);
			if (emailService.validatePet(pet) && emailService.validationService.validateOwner(owner)) {
				emailService.emailMap.put(owner, owner.getEmail());
				processedCount++;
			}
		}
		log.info("Email DB processing completed: {} owner(s) prepared", processedCount);

		redirectAttributes.addFlashAttribute("message", "Emails sent to " + allOwners.size() + " owner(s)");
		return "redirect:/";
	}

	@PostMapping("/emails/all/range")
	public String sendEmailsToAllInRange(@RequestParam("fromOwnerId") int fromOwnerId, @RequestParam("toOwnerId") int toOwnerId,
	                                     RedirectAttributes redirectAttributes) {
		List<Owner> allOwners = this.owners.findAll();

		if (allOwners.isEmpty()) {
			redirectAttributes.addFlashAttribute("message", "No owners found");
			return "redirect:/";
		}

		if (fromOwnerId > toOwnerId) {
			redirectAttributes.addFlashAttribute("error", "Invalid owner range");
			return "redirect:/";
		}

		int sentCount = 0;
		for (Owner owner : allOwners) {
			Pet pet = getFirstPet(owner);
			boolean ownerInRange = owner.getId() != null && owner.getId() >= fromOwnerId && owner.getId() <= toOwnerId;
			if (ownerInRange) {
				emailService.sendEmail(owner, pet, redirectAttributes);
				sentCount++;
			}
		}

		redirectAttributes.addFlashAttribute("message", "Emails sent to " + sentCount + " owner(s)");
		return "redirect:/";
	}

	@PostMapping("/emails/cleanup")
	public String cleanupEmails(RedirectAttributes redirectAttributes) {
		List<Owner> allOwners = this.owners.findAll();

		if (allOwners.isEmpty()) {
			redirectAttributes.addFlashAttribute("message", "No owners found");
			return "redirect:/";
		}

		for (Owner owner : allOwners) {
			if (!owner.getEmail().contains("@")) {
				emailService.emailMap.remove(owner);
			}
		}

		redirectAttributes.addFlashAttribute("message", "Emails Database was updated");
		return "redirect:/";
	}

	@PostMapping("/emails/owner")
	public String sendEmailsToOwner(@RequestParam("ownerId") int ownerId, RedirectAttributes redirectAttributes) {
		ISpan span = Sentry.getSpan();
		if (span != null) {
			span.setData("ownerId", ownerId);
			span.setTag("ownerId", String.valueOf(ownerId));
		}

		List<Owner> allOwners = this.owners.findAll();

		if (allOwners.isEmpty()) {
			redirectAttributes.addFlashAttribute("message", "No owners found");
			return "redirect:/";
		}

		Optional<Owner> ownerOptional = this.owners.findById(ownerId);
		if (!ownerOptional.isPresent()) {
			redirectAttributes.addFlashAttribute("error", "Owner not found");
			return "redirect:/";
		}

		Pet pet = getFirstPet(ownerOptional.get());
		emailService.sendEmail(ownerOptional.get(), pet, redirectAttributes);

		redirectAttributes.addFlashAttribute("message", "Emails sent to " + ownerOptional.get().getEmail() + " owner");
		return "redirect:/owners/" + ownerId;
	}

	/**
	 * Triggers the /sms/city/{cityName} endpoint in strict mode (used by background
	 * thread)
	 */
	private void triggerSmsByCityEndpoint(String cityName) {
		List<Owner> cityOwners = this.owners.findByCity(cityName);

		for (Owner owner : cityOwners) {
			// Get first pet or null if no pets
			// Use strict mode by calling sendSms with owner and pet
			Pet pet = getFirstPet(owner);
			smsService.sendSms(owner, pet);
		}
	}

}
