# Location-Based Patient Data Filter Module

This module provides **location-based filtering** for patients in OpenMRS. It ensures that users only see patients assigned to locations they have access to. This is especially useful for restricting access in multi-location setups.

---

## Features

- Apply **Hibernate filters** to the `Patient` entity based on user locations.
- Supports fetching **user-accessible locations** from:
    - User properties
    - User privileges
- Automatically includes **child locations** for users.
- Exempts **super users** from filters.
- Can be extended for additional entity filters.

---

## Hibernate Filter Definition

The module provides the following Hibernate filter:

```json
[
  {
    "name": "patient_location_based_filter",
    "targetClasses": [
      "org.openmrs.Patient"
    ],
    "condition":
      "patient_id IN (
        SELECT p.patient_id FROM patient p
        INNER JOIN person_address pa ON p.patient_id = pa.person_id
        WHERE pa.location_id IN (:locationIds)
      )",
    "parameters": [
      {
        "name": "locationIds",
        "type": "integer"
      }
    ]
  }
]
```
## How it works

- Targets the **Patient** entity.
- Filters patients whose **address location** is in the `locationIds` parameter.
- The parameter `locationIds` is dynamically populated by the `LocationBasedPatientDataFilterListener`.

---

## Location-Based Filter Listener

The listener handles enabling the filter at runtime:

- Skips filter for **super users**.
- Fetches accessible locations for the authenticated user:
    - From **user properties**
    - From **user privileges** (if no property exists)
- Automatically includes **child locations** for each accessible location.
- Injects the **location IDs** into the Hibernate filter.

---

## Usage

- Include the module in your OpenMRS modules directory.
- The Hibernate filter is automatically applied to all **Patient** queries.
- Extend the listener or filters if you need other **entity-based location restrictions**.

---

## Notes

- **Super users** bypass all location filters.
- **Child locations** are recursively included in the filter.
- Users without a defined location property will have access determined by their **privileges**.
- The filter is applied **transparently at the Hibernate level**.
