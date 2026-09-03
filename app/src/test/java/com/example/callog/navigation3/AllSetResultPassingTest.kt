package com.example.callog.navigation3

import com.example.callog.presentation.navigation3.allset.AllSetBackStack
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import com.example.callog.presentation.navigation3.allset.AllSetResultManager
import org.junit.Assert.*
import org.junit.Test

class AllSetResultPassingTest {

    @Test
    fun testEditContactResultPassing() {
        val resultManager = AllSetResultManager()
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Contacts.ContactDetails("C001")), resultManager)

        // Navigate to EditContact
        stack.navigate(AllSetNavKey.Contacts.EditContact("C001"))
        assertEquals(AllSetNavKey.Contacts.EditContact("C001"), stack.currentKey)

        // Save updated name and pop
        val updatedName = "Rahul Sharma"
        resultManager.setResult("contact_updated_C001", updatedName)
        stack.pop()

        // Caller screen retrieves result
        assertTrue(resultManager.hasResult("contact_updated_C001"))
        val consumed = resultManager.consumeResult<String>("contact_updated_C001")
        assertEquals("Rahul Sharma", consumed)
        assertFalse(resultManager.hasResult("contact_updated_C001"))
    }

    @Test
    fun testAddFeedbackResultPassing() {
        val resultManager = AllSetResultManager()
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Contacts.ContactDetails("C001")), resultManager)

        stack.navigate(AllSetNavKey.Contacts.AddFeedback("C001"))
        resultManager.setResult("feedback_C001", Pair(5, "Very interested in the enterprise plan"))
        stack.pop()

        val result = resultManager.consumeResult<Pair<Int, String>>("feedback_C001")
        assertNotNull(result)
        assertEquals(5, result?.first)
        assertEquals("Very interested in the enterprise plan", result?.second)
    }

    @Test
    fun testTaskCreationResultPassing() {
        val resultManager = AllSetResultManager()
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Tasks.List), resultManager)

        stack.navigate(AllSetNavKey.Tasks.CreateTask("C001"))
        resultManager.setResult("task_created", "task-102")
        stack.pop()

        val createdTaskId = resultManager.consumeResult<String>("task_created")
        assertEquals("task-102", createdTaskId)
    }
}
