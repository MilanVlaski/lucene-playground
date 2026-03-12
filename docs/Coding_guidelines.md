- Prefer final in properties, which promotes immutable classes.
- Only one simple constructor, that all other constructors call.
- Prefer naming classes and interfaces as one-word nouns, avoiding "agent nouns", like xManager and xHelper. xService is also generally pointless, but has uses.
- The code in the core of the app should read like a DSL. Stupid simple.
- Don't hesitate to use an object as a function, for example `new Action(param).execute();`.
- Use Records, over anemic class.
- No getters, no setters. Getters may exist, but are named like properties of Java records.
- Use generics sparingly.
- No frameworks, no annotations.

## Ports & Adapters (Hexagonal) Architecture

- The core has zero dependencies on frameworks.
- The core depends on interfaces that are dependency injected, usually manually, to the constructor (in 99% of cases).
- There are two types of ports, defined by Java interfaces:
	- Driving ports: through which a CLI, web, or test can invoke methods of the core. May also be called API (Application Programming Interface).
	- Driven ports:  Always defined **IN THE LANGUAGE OF THE CORE**. This promotes a DSL-like approach. May also be called a SPI (Service Provider Interface).
- **Driven Adapters** (like a Database Repository) must map their specific database entities (e.g., ORM models) into **Core Domain Objects** before returning them to the Core.
- The Core should be able to run in a "headless" state. If you can't run your entire business logic through a unit test without starting a database or a web server, the architecture isn't truly hexagonal.
