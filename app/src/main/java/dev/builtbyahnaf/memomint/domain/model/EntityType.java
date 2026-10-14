package dev.builtbyahnaf.memomint.domain.model;

/**
 * Classifies the kind of concept represented by an Entity record.
 *
 * <p>Entity type guides synonym lookup, relationship inference, and UI presentation.
 * It is assigned during entity extraction and stored alongside the entity's canonical label.
 * An uncertain classification should be stored as OTHER rather than forced into a wrong type.</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum EntityType {

    /**
     * A physical object: earbuds, USB-C cable, dust blower, box of pens, etc.
     */
    ITEM,

    /**
     * A place: a drawer, a wardrobe, a room, an address, a building, a campus.
     */
    LOCATION,

    /**
     * A named person referenced in a memory.
     */
    PERSON,

    /**
     * A subject or theme: guitar prices, project setup steps, Android APIs.
     */
    TOPIC,

    /**
     * An entity whose type could not be reliably determined from the available context.
     */
    OTHER
}
