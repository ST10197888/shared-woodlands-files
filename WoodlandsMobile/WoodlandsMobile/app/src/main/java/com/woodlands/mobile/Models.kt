package com.woodlands.mobile

data class Product(val id:Int,val category:String,val title:String,val tagline:String,val description:String,val image:String,val gallery:List<String>,val features:List<String>,val finishes:List<String>,val lead:String,val tag:String?,val price:String)
data class Testimonial(val id:Int,val name:String,val role:String,val location:String,val rating:Int,val review:String,val project:String)
data class Faq(val id:Int,val category:String,val question:String,val answer:String)

data class Branch(val id:Int,val name:String,val region:String,val phone:String,val hours:String,val notes:String,val image:String="",val address:String="")

data class AppUser(
    val id:String,
    val fullName:String,
    val email:String,
    val phone:String?,
    val role:String,
    val branch:String?,
    val active:Boolean,
    val createdAt:Long
)

data class QuoteRow(
    val id:Long,
    val quoteCode:String,
    val firstName:String,
    val lastName:String,
    val email:String,
    val phone:String,
    val branch:String,
    val service:String,
    val message:String,
    val productId:String?,
    val createdAt:Long,
    val status:String,
    val value:String?
)

object Roles {
    const val ADMIN = "Admin"
    const val MANAGER_SOWETO = "Manager (Soweto)"
    const val MANAGER_ROODEPOORT = "Manager (Roodepoort)"
    const val MANAGER_RANDFONTEIN = "Manager (Randfontein)"
    const val CUSTOMER = "Customer"

    val MANAGERS = listOf(MANAGER_SOWETO, MANAGER_ROODEPOORT, MANAGER_RANDFONTEIN)
    val ALL = listOf(ADMIN, MANAGER_SOWETO, MANAGER_ROODEPOORT, MANAGER_RANDFONTEIN, CUSTOMER)

    fun branchFor(role:String):String? = when(role){
        MANAGER_SOWETO->"Soweto"; MANAGER_ROODEPOORT->"Roodepoort"; MANAGER_RANDFONTEIN->"Randfontein"; else->null
    }

    fun roleForBranch(branch:String):String = when(branch){
        "Soweto"->MANAGER_SOWETO; "Roodepoort"->MANAGER_ROODEPOORT; "Randfontein"->MANAGER_RANDFONTEIN; else->CUSTOMER
    }

    fun label(role:String):String = when(role){
        ADMIN->"System Admin"
        MANAGER_SOWETO->"Branch Manager · Soweto"
        MANAGER_ROODEPOORT->"Branch Manager · Roodepoort"
        MANAGER_RANDFONTEIN->"Branch Manager · Randfontein"
        CUSTOMER->"Customer"
        else->role
    }

    fun isManager(role:String) = role in MANAGERS
    fun isStaff(role:String) = role == ADMIN || isManager(role)
}
