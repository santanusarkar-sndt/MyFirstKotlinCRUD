package com.springernature

import com.springernature.data.Employee
import com.springernature.formats.kotlinXMessage
import com.springernature.formats.kotlinXMessageLens
import com.springernature.routes.ExampleContractRoute
import kotlinx.serialization.json.Json
import org.http4k.contract.bind
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.contract.security.ApiKeySecurity
import org.http4k.core.*
import org.http4k.core.Method.GET
import org.http4k.core.Status.Companion.OK
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.format.KotlinxSerialization.json
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction

val employeeJsonLens = Body.json().toLens()
val employeeLens = Body.auto<Employee>().toLens()
val employeeListLens = Body.auto<List<Employee>>().toLens()
val employeeIdLens = Query.int().required("id")

val json = Json
val app: HttpHandler = routes(
    "/ping" bind GET to {
        Response(OK).body("pong")
    },
    "/getEmployeeDetails" bind GET to {
        var allOutcomes: List<Employee> = ArrayList()
        println(Employee(12, "efgh", 31))
        transaction {
            allOutcomes = EMPLOYEE.selectAll().map { emp ->
                Employee(emp[EMPLOYEE.id], emp[EMPLOYEE.name], emp[EMPLOYEE.age])
            }
        }
        println(allOutcomes)
        Response(OK).with(employeeListLens of allOutcomes)
    },

    "/addEmployee" bind Method.POST to {
        var employeeJson = employeeJsonLens.extract(it)
        println(employeeJson)

        var employee: Employee = employeeLens.extract(it) //json.decodeFromString(employeeJson.asPrettyJsonString())
        println(employee.name)
        transaction {
            EMPLOYEE.insert {
                it[name] = employee.name
                it[id] = employee.id
                it[age] = employee.age


            }
        }
        Response(OK)
    },
    "/updateEmployee" bind Method.PUT to {


        var employee: Employee = employeeLens.extract(it) //json.decodeFromString(employeeJson.asPrettyJsonString())
        transaction {
            EMPLOYEE.update({ EMPLOYEE.id eq employee.id }) {
                it[name] = employee.name
                it[age] = employee.age
            }
        }
        Response(OK)
    },
    "/deleteEmployee" bind Method.DELETE to {

        val id = employeeIdLens.extract(it)
        //json.decodeFromString(employeeJson.asPrettyJsonString())
        transaction {
            EMPLOYEE.deleteWhere { EMPLOYEE.id eq id }
        }
        Response(OK)
    },
    "/formats/json/kotlinx" bind GET to {
        Response(OK).with(kotlinXMessageLens of kotlinXMessage)
    },

    "/contract/api/v1" bind contract {
        renderer = OpenApi3(ApiInfo("MyFirstKotlinCRUD API", "v1.0"))

        // Return Swagger API definition under /contract/api/v1/swagger.json
        descriptionPath = "/swagger.json"

        // You can use security filter tio protect routes
        security = ApiKeySecurity(Query.int().required("api"), { it == 42 }) // Allow only requests with &api=42

        // Add contract routes
        routes += ExampleContractRoute()
    }
)

fun main() {
    val printingApp: HttpHandler = PrintRequest().then(app)

    val server = printingApp.asServer(SunHttp(9000)).start()

    Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction {
        create(EMPLOYEE)
        EMPLOYEE.insert {
            it[name] = "abc"
            it[id] = 1
            it[age] = 42
        }
    }
    println("Server started on " + server.port())
}

object EMPLOYEE : org.jetbrains.exposed.sql.Table() {
    val name = varchar("name", 25)
    val id = integer("id").uniqueIndex()
    val age = integer("age")

}
