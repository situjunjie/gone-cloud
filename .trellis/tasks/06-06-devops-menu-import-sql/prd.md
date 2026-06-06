# devops menu import sql

## Goal

Create a MySQL import script for DevOps admin menus and button permissions based on the frontend page and permission information supplied by the user.

## Requirements

* Add a SQL file under `sql/mysql/`.
* Do not use fixed menu IDs.
* Use SQL variables to resolve parent menu IDs after insert/select.
* Make the script safe to import repeatedly by checking existing menu rows before inserting.
* Include DevOps directory, application/environment/change page menus, and button permission items.

## Acceptance Criteria

* [ ] SQL file creates the DevOps menu hierarchy using variables.
* [ ] Script avoids duplicate rows by checking existing rows.
* [ ] Button permission values match backend/frontend permissions.
* [ ] `git diff --check` passes.

## Out of Scope

* Executing the SQL against a live database.
* Adding code source provider menu, because the frontend information in this request only includes application/environment/change pages.
