package com.ups.service;

import com.ups.model.MessageLog;
import com.ups.repository.MessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MessageTrackingServiceTest {

    private MessageTrackingService messageTrackingService;

    @Mock
    private MessageLogRepository messageLogRepository;

    @BeforeEach
    void setUp() {
        messageTrackingService = new MessageTrackingService(messageLogRepository);
    }

    @Test
    void testGetNextSeqNum() {
        // Each call should increment the sequence number
        long seq1 = messageTrackingService.getNextSeqNum();
        long seq2 = messageTrackingService.getNextSeqNum();
        long seq3 = messageTrackingService.getNextSeqNum();

        assertEquals(1, seq1);
        assertEquals(2, seq2);
        assertEquals(3, seq3);
    }

    @Test
    void testRecordOutgoingMessage() {
        // Arrange
        long seqNum = 42;
        String messageType = "TEST_MESSAGE";

        // Act
        messageTrackingService.recordOutgoingMessage(seqNum, messageType);

        // Assert
        ArgumentCaptor<MessageLog> captor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(captor.capture());

        MessageLog savedLog = captor.getValue();
        assertEquals(seqNum, savedLog.getSeqNum());
        assertEquals(messageType, savedLog.getMessageType());
        assertEquals("OUTGOING", savedLog.getDirection());
        assertNotNull(savedLog.getTimestamp());
        assertNull(savedLog.getAcknowledged());
    }

    @Test
    void testAcknowledgeMessage() {
        // Arrange
        long seqNum = 42;
        MessageLog existingLog = new MessageLog();
        existingLog.setSeqNum(seqNum);
        existingLog.setMessageType("TEST_MESSAGE");
        existingLog.setDirection("OUTGOING");
        existingLog.setTimestamp(Instant.now());

        when(messageLogRepository.findBySeqNum(seqNum)).thenReturn(existingLog);

        // Act
        boolean result = messageTrackingService.acknowledgeMessage(seqNum);

        // Assert
        assertTrue(result);
        assertNotNull(existingLog.getAcknowledged());
        verify(messageLogRepository).save(existingLog);
    }

    @Test
    void testAcknowledgeNonExistentMessage() {
        // Arrange
        long seqNum = 42;
        when(messageLogRepository.findBySeqNum(seqNum)).thenReturn(null);

        // Act
        boolean result = messageTrackingService.acknowledgeMessage(seqNum);

        // Assert
        assertFalse(result);
        verify(messageLogRepository, never()).save(any());
    }

    @Test
    void testGetUnacknowledgedMessages() {
        // This would typically query the database for unacknowledged messages
        // Implementation would depend on how the repository is set up
        messageTrackingService.getUnacknowledgedMessages();
        verify(messageLogRepository).findByAcknowledgedIsNullAndDirection("OUTGOING");
    }
} 