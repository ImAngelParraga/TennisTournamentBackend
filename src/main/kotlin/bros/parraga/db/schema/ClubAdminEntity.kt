package bros.parraga.db.schema

import org.jetbrains.exposed.sql.Table

object ClubAdminsTable : Table("club_admins") {
    val clubId = reference("club_id", ClubsTable)
    val userId = reference("user_id", UsersTable)

    override val primaryKey = PrimaryKey(clubId, userId, name = "PK_club_admins")
}
