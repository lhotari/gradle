- Implement `CompositeBuildConnection` implemented top of Tooling API : Client-side tooling API only
    - Adding `CompositeBuildConnector`
    - Creating `ProjectConnection` instances for each participant
    - Delegating all `getModels()` calls to each `ProjectConnection` : only can cope with `EclipseProject`
        - Fail for any request other than `EclipseProject`
    - Flatten set of `EclipseProject` instances and create a `ProjectIdentity` for each
    - Real code in the Tooling API in Gradle

- Implement naive dependency substitution in Client-side CBC
    - Get the ProjectPublications instances for every `EclipseProject` in the composite
        - Create a `ProjectConnection` for each subproject, and ask for the model
    - Much like the spike: adapt the returned set of Eclipse project instances
    - Real code in the Tooling API in Gradle

- Implement CompositeBuildConnection as a new part of Tooling API talking to daemon
    - Remoting the existing code
    - Move the Client-side CBC implementation into the daemon request handler

- Implement real dependency substitution for composite build with a single Gradle version
    - Removing the use of ToolingAPI to communicate with each participant
    - Provide the "Composite Context" (containing all project publication information) when requesting `EclipseProject` model from each build

- Implement real dependency substitution for composite build with a different Gradle versions
    - ??? Is this just like doing it from the client ???

- Implement deduplication in Client-side CBC
    - Much like the spike: adapt the returned set of Eclipse project instances
    - Problematic: we think de-duplication should be deterministic, and not dependent on the order builds are added to the composite

- Migrate spike to expose new API
- Design spec for real implementation stories above
- Implement stories above