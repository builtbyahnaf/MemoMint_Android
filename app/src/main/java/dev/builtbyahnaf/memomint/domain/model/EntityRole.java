package dev.builtbyahnaf.memomint.domain.model;

/**
 * Describes the role that an Entity plays within a specific Memory.
 *
 * <p>The same entity may appear in multiple memories with different roles. For example,
 * "wardrobe" may be the LOCATION in one memory and the OBJECT of an observation in another.
 * The role is stored in the MemoryEntity join record, not in the Entity itself.</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum EntityRole {

    /**
     * The entity is the primary subject of the memory.
     * Example: earbuds are the SUBJECT of "My earbuds are in the drawer."
     */
    SUBJECT,

    /**
     * The entity describes where something is or was located.
     * Example: "desk drawer" is the LOCATION in "My cable is in the desk drawer."
     */
    LOCATION,

    /**
     * The entity is the direct object of an action or observation in the memory.
     */
    OBJECT,

    /**
     * The entity represents the general subject or category of the memory.
     * Example: "guitar prices" is the TOPIC of a document comparison note.
     */
    TOPIC
}
