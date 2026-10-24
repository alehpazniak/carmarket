package com.carmarket.auction.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * One feed sync at a time across every instance of the service: a session-level pg_try_advisory_lock held on its
 * own connection for the whole run. Postgres releases it if the connection dies, so a crash can't leave it stuck.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncLock {

    /** "AUCTSYNC" */
    static final long KEY = 0x4155_4354_5359_4E43L;

    private final DataSource dataSource;

    public interface Held extends AutoCloseable {
        @Override
        void close();
    }

    /** The lock, or empty when another run holds it. */
    public Optional<Held> tryAcquire() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean locked;
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, KEY);
                try (ResultSet result = statement.executeQuery()) {
                    locked = result.next() && result.getBoolean(1);
                }
            }
            if (!locked) {
                connection.close();
                return Optional.empty();
            }
            Connection held = connection;
            return Optional.of(() -> release(held));
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new IllegalStateException("Could not take the sync lock", e);
        }
    }

    private static void release(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, KEY);
            statement.execute();
            closeQuietly(connection);
        } catch (SQLException e) {
            // close() would only hand the connection back to the pool, still holding the lock: drop it instead
            log.warn("Could not release the sync lock, dropping its connection: {}", e.getMessage());
            try {
                connection.abort(Runnable::run);
            } catch (SQLException ignored) {
                closeQuietly(connection);
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
            // nothing left to do
        }
    }
}
