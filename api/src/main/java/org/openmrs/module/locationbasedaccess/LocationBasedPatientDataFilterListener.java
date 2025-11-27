package org.openmrs.module.locationbasedaccess;

import org.apache.commons.lang.StringUtils;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.datafilter.DataFilterContext;
import org.openmrs.module.datafilter.DataFilterListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collections;

public class LocationBasedPatientDataFilterListener implements DataFilterListener {
	
	private static final Logger log = LoggerFactory.getLogger(LocationBasedPatientDataFilterListener.class);
	
	@Override
    public boolean onEnableFilter(DataFilterContext filterContext) {

        // Skip filter if super user
        if (Context.isAuthenticated() && Context.getAuthenticatedUser().isSuperUser()) {
            log.trace("Skipping patient location filter for super user");
            return false;
        }

        // Only process our filter
        if (!filterContext.getFilterName().startsWith("patient_location_based_filter")) {
            return true;
        }

        User user = Context.getAuthenticatedUser();
        log.trace("Enabling location-based patient filter for user {}", user.getUsername());

        // Get locations from user property
        List<String> allowed = getUserAccessibleLocationUuids(user);

        // If not set → use privilege-based access
        if (allowed == null || allowed.isEmpty() || allowed.get(0) == null) {
            allowed = getUserAccessibleLocationUuidsWithUserPrivileges(user);
        }

        // If still empty → no restriction (user sees nothing)
        if (allowed == null || allowed.isEmpty()) {
            log.warn("User {} has NO location configured. Patient results will be empty.", user.getUsername());
            allowed = Collections.emptyList();
        }

        // Expand to include all child locations
        Set<String> expanded = new HashSet<>();
        for (String uuid : allowed) {
            Location loc = Context.getLocationService().getLocationByUuid(uuid);
            if (loc != null) {
                expanded.add(loc.getUuid());
                expanded.addAll(getAllChildLocationUuids(loc));
            }
        }

        log.debug("User {} final accessible patient locations (including children): {}",
                user.getUsername(), expanded);

        // Apply to filter
        filterContext.setParameter("locationUuids", expanded);

        return true; // allow other filters
    }
	
	@Override
	public boolean supports(String filterName) {
		return filterName.startsWith("patient_location_based_filter");
	}
	
	/**
	 * Used to get the accessible locations for the user. It will first get from the user property.
	 * if the user property is not available, then check for the session location. If both are not
	 * available then return null.
	 * 
	 * @param authenticatedUser Authenticated user
	 * @return accessibleLocationUuids the list of accessible Locations uuid
	 */
	public static List<String> getUserAccessibleLocationUuids(User authenticatedUser) {
		if (authenticatedUser == null) {
			return null;
		}
		List<String> accessibleLocationUuids;
		String accessibleLocationUuid = authenticatedUser
		        .getUserProperty(LocationBasedAccessConstants.LOCATION_USER_PROPERTY_NAME);
		if (StringUtils.isBlank(accessibleLocationUuid)) {
			Integer sessionLocationId = Context.getUserContext().getLocationId();
			if (sessionLocationId != null) {
				accessibleLocationUuid = Context.getLocationService().getLocation(sessionLocationId).getUuid();
			}
		}
		accessibleLocationUuids = Arrays.asList(accessibleLocationUuid.split(","));
		return accessibleLocationUuids;
	}
	
	/**
	 * Used to get the accessible locations for the user. It will first get from the user property.
	 * if the user property is not available, then check for the locations having user privilege. If
	 * both are not available then return null.
	 * 
	 * @param authenticatedUser Authenticated user
	 * @return accessible Location uuid
	 */
	public static List<String> getUserAccessibleLocationUuidsWithUserPrivileges(User authenticatedUser) {
		if (authenticatedUser == null) {
			return null;
		}
		List<String> accessibleLocationUuids = new ArrayList<String>();
		String accessibleLocationUuid = authenticatedUser
		        .getUserProperty(LocationBasedAccessConstants.LOCATION_USER_PROPERTY_NAME);
		if (StringUtils.isBlank(accessibleLocationUuid)) {
			List<Location> openMrsLocations = Context.getLocationService().getAllLocations();
			for (Location location : openMrsLocations) {
				if (authenticatedUser.hasPrivilege("LocationAccess " + location.getName())) {
					accessibleLocationUuids.add(location.getUuid());
				}
			}
		} else {
			accessibleLocationUuids = Arrays.asList(accessibleLocationUuid.split(","));
		}
		return accessibleLocationUuids;
	}
	
	//	/**
	//	 * Get all child location ids for the user's accessible locations
	//	 *
	//	 * @return set of location ids
	//	 */
	//	public static Location getPersonLocation(Person person) {
	//		String locationAttributeUuid = Context.getAdministrationService().getGlobalProperty(
	//		    LocationBasedAccessConstants.LOCATION_ATTRIBUTE_GLOBAL_PROPERTY_NAME);
	//		if (StringUtils.isNotBlank(locationAttributeUuid)) {
	//			final PersonAttributeType personAttributeType = Context.getPersonService().getPersonAttributeTypeByUuid(
	//			    locationAttributeUuid);
	//			PersonAttribute personAttribute = person.getAttribute(personAttributeType);
	//			if (personAttribute != null) {
	//				Location personLocation = Context.getLocationService().getLocationByUuid(personAttribute.getValue());
	//				return personLocation;
	//			}
	//		}
	//		return null;
	//	}
	
	/**
	 * Recursively finds all descendant (child) locations
	 */
	private Set<String> getAllChildLocationUuids(Location parent) {
        Set<String> results = new HashSet<>();
        if (parent.getChildLocations() == null) {
            return results;
        }

        for (Location child : parent.getChildLocations()) {
            results.add(child.getUuid());
            results.addAll(getAllChildLocationUuids(child));
        }

        return results;
    }
}
