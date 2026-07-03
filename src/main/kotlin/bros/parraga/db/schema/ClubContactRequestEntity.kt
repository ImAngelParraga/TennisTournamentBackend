package bros.parraga.db.schema

import bros.parraga.domain.ClubContactRequest
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object ClubContactRequestsTable : IntIdTable("club_contact_requests") {
    val clubName = varchar("club_name", 255)
    val contactName = varchar("contact_name", 255)
    val email = varchar("email", 255)
    val phone = varchar("phone", 50).nullable()
    val message = text("message").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

class ClubContactRequestDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClubContactRequestDAO>(ClubContactRequestsTable)

    var clubName by ClubContactRequestsTable.clubName
    var contactName by ClubContactRequestsTable.contactName
    var email by ClubContactRequestsTable.email
    var phone by ClubContactRequestsTable.phone
    var message by ClubContactRequestsTable.message
    var createdAt by ClubContactRequestsTable.createdAt

    fun toDomain() = ClubContactRequest(
        id = id.value,
        clubName = clubName,
        contactName = contactName,
        email = email,
        phone = phone,
        message = message,
        createdAt = createdAt.toKotlinInstant()
    )
}
