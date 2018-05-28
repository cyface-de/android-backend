package de.cyface.synchronization;

public final class SyncProgress {
    private long countOfTransmittedPoints = 0L;
    private long countOfPointsToTransmitt = 0L;

    public void resetProgress() {
        countOfTransmittedPoints = 0L;
        countOfPointsToTransmitt = 1L;
    }
}
