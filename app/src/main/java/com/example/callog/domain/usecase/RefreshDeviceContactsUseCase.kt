package com.example.callog.domain.usecase

import com.example.callog.domain.repository.DeviceContactsRepository
import javax.inject.Inject

/**
 * Use case to refresh local device contacts from Android ContactsContract.
 */
class RefreshDeviceContactsUseCase @Inject constructor(
    private val deviceContactsRepository: DeviceContactsRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return deviceContactsRepository.refreshContacts()
    }
}
