package org.example.all_my_trip_project.domain.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.booking.config.BookingQueueProperties;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueState;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueStatusResponse;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("!ui")
@RequiredArgsConstructor
class RedisBookingQueueStore implements BookingQueueStore {

    private static final String PREFIX = "all-my-trips:booking-queue:";
    private static final String ENTRY_PREFIX = PREFIX + "entry:";
    private static final String USER_PREFIX = PREFIX + "slot:";

    private static final DefaultRedisScript<String> ENQUEUE_SCRIPT = script("""
            local existing = redis.call('GET', KEYS[3])
            if existing then
              local existingKey = ARGV[2] .. existing
              if redis.call('EXISTS', existingKey) == 1 then
                local existingState = redis.call('HGET', existingKey, 'state')
                local existingPosition = 0
                if existingState == 'WAITING' then
                  local existingRank = redis.call('ZRANK', KEYS[1], existing)
                  if existingRank then existingPosition = existingRank + 1 end
                end
                return cjson.encode({
                  token = existing,
                  status = existingState,
                  slotId = redis.call('HGET', existingKey, 'slotId'),
                  tripId = redis.call('HGET', existingKey, 'tripId'),
                  position = existingPosition,
                  initialPosition = redis.call('HGET', existingKey, 'initialPosition') or '0',
                  expiresAt = redis.call('HGET', existingKey, 'expiresAt')
                })
              end
              redis.call('DEL', KEYS[3])
            end

            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[9])
            local queued = redis.call('ZCARD', KEYS[1])
            local state = 'WAITING'
            if queued == 0 then
              local admitted = redis.call('INCR', KEYS[4])
              if admitted == 1 then redis.call('EXPIRE', KEYS[4], 2) end
              if admitted <= tonumber(ARGV[11]) then state = 'READY' end
            end

            local expiresAt = tonumber(ARGV[8]) + tonumber(ARGV[10]) * 1000
            redis.call('HSET', KEYS[2],
              'token', ARGV[1], 'state', state, 'userId', ARGV[3], 'slotId', ARGV[4],
              'tripId', ARGV[5], 'quantity', ARGV[6], 'requestKey', ARGV[7],
              'createdAt', ARGV[8], 'expiresAt', tostring(expiresAt))
            redis.call('EXPIRE', KEYS[2], ARGV[10])
            redis.call('SET', KEYS[3], ARGV[1], 'EX', ARGV[10])

            local position = 0
            if state == 'WAITING' then
              redis.call('ZADD', KEYS[1], ARGV[8], ARGV[1])
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[10]) * 2)
              position = redis.call('ZCARD', KEYS[1])
            end
            redis.call('HSET', KEYS[2], 'initialPosition', tostring(position))
            return cjson.encode({
              token = ARGV[1], status = state, slotId = ARGV[4], tripId = ARGV[5],
              position = position, initialPosition = position, expiresAt = expiresAt
            })
            """);

    private static final DefaultRedisScript<String> STATUS_SCRIPT = script("""
            if redis.call('EXISTS', KEYS[2]) == 0 then
              return cjson.encode({status = 'EXPIRED', token = ARGV[2]})
            end
            if redis.call('HGET', KEYS[2], 'userId') ~= ARGV[1] then
              return cjson.encode({status = 'FORBIDDEN', token = ARGV[2]})
            end

            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
            local current = tonumber(redis.call('GET', KEYS[4]) or '0')
            local remaining = tonumber(ARGV[5]) - current
            local promoted = 0
            if remaining > 0 then
              local candidates = redis.call('ZRANGE', KEYS[1], 0, remaining - 1)
              for _, candidate in ipairs(candidates) do
                local entryKey = ARGV[8] .. candidate
                if redis.call('EXISTS', entryKey) == 1 then
                  local readyExpiresAt = tonumber(ARGV[4]) + tonumber(ARGV[6]) * 1000
                  redis.call('HSET', entryKey, 'state', 'READY', 'expiresAt', tostring(readyExpiresAt))
                  redis.call('EXPIRE', entryKey, ARGV[6])
                  local owner = redis.call('HGET', entryKey, 'userId')
                  local slot = redis.call('HGET', entryKey, 'slotId')
                  local ownerKey = ARGV[9] .. slot .. ':user:' .. owner
                  if redis.call('EXISTS', ownerKey) == 1 then redis.call('EXPIRE', ownerKey, ARGV[6]) end
                  promoted = promoted + 1
                end
                redis.call('ZREM', KEYS[1], candidate)
              end
              if promoted > 0 then
                redis.call('INCRBY', KEYS[4], promoted)
                redis.call('EXPIRE', KEYS[4], 2)
              end
            end

            local state = redis.call('HGET', KEYS[2], 'state')
            local position = 0
            if state == 'WAITING' then
              local rank = redis.call('ZRANK', KEYS[1], ARGV[2])
              if not rank then
                redis.call('DEL', KEYS[2], KEYS[3])
                return cjson.encode({status = 'EXPIRED', token = ARGV[2]})
              end
              position = rank + 1
            end
            return cjson.encode({
              token = ARGV[2], status = state,
              slotId = redis.call('HGET', KEYS[2], 'slotId'),
              tripId = redis.call('HGET', KEYS[2], 'tripId'),
              position = position,
              initialPosition = redis.call('HGET', KEYS[2], 'initialPosition') or tostring(position),
              expiresAt = redis.call('HGET', KEYS[2], 'expiresAt')
            })
            """);

    private static final DefaultRedisScript<String> CLAIM_SCRIPT = script("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return cjson.encode({status = 'EXPIRED'})
            end
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then
              return cjson.encode({status = 'FORBIDDEN'})
            end
            local state = redis.call('HGET', KEYS[1], 'state')
            if state == 'COMPLETED' then
              return cjson.encode({status = state, reservation = redis.call('HGET', KEYS[1], 'reservation')})
            end
            if state ~= 'READY' then return cjson.encode({status = state, claimed = false}) end
            local expiresAt = tonumber(ARGV[3]) + tonumber(ARGV[4]) * 1000
            redis.call('HSET', KEYS[1], 'state', 'PROCESSING', 'expiresAt', tostring(expiresAt))
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('EXPIRE', KEYS[3], ARGV[4])
            return cjson.encode({
              status = 'PROCESSING', claimed = true,
              tripId = redis.call('HGET', KEYS[1], 'tripId'),
              slotId = redis.call('HGET', KEYS[1], 'slotId'),
              quantity = redis.call('HGET', KEYS[1], 'quantity'),
              requestKey = redis.call('HGET', KEYS[1], 'requestKey')
            })
            """);

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = longScript("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then return -1 end
            local expiresAt = tonumber(ARGV[3]) + tonumber(ARGV[4]) * 1000
            redis.call('HSET', KEYS[1], 'state', 'COMPLETED', 'reservation', ARGV[2],
              'expiresAt', tostring(expiresAt))
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('ZREM', KEYS[2], ARGV[5])
            redis.call('DEL', KEYS[3])
            return 1
            """);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = longScript("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then return -1 end
            if redis.call('HGET', KEYS[1], 'state') ~= 'PROCESSING' then return 0 end
            local expiresAt = tonumber(ARGV[2]) + tonumber(ARGV[3]) * 1000
            redis.call('HSET', KEYS[1], 'state', 'READY', 'expiresAt', tostring(expiresAt))
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            redis.call('SET', KEYS[2], ARGV[4], 'EX', ARGV[3])
            return 1
            """);

    private static final DefaultRedisScript<Long> CANCEL_SCRIPT = longScript("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then return -1 end
            redis.call('ZREM', KEYS[2], ARGV[2])
            redis.call('DEL', KEYS[1], KEYS[3])
            return 1
            """);

    private final StringRedisTemplate redisTemplate;
    // Spring Boot 4는 Jackson 3을 자동 구성하므로 Jackson 2의 ObjectMapper 빈이 없다.
    // 주입받으면 ui 이외의 프로필에서 컨텍스트가 뜨지 않는다. 다른 서비스와 같이 직접 만든다.
    // 예약 DTO에 LocalDate·LocalTime이 있어 모듈 등록 없이는 직렬화가 실패한다.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final BookingQueueProperties properties;

    @Override
    public BookingQueueStatusResponse enqueue(
            Long userId,
            CreateTicketReservationRequest request,
            String token,
            Instant now
    ) {
        long nowMillis = now.toEpochMilli();
        return executeStatus(ENQUEUE_SCRIPT, List.of(
                        queueKey(request.slotId()), entryKey(token), userKey(request.slotId(), userId),
                        throughputKey(request.slotId(), now)
                ),
                token, ENTRY_PREFIX, userId, request.slotId(), request.tripId(), request.quantity(),
                request.requestKey().trim(), nowMillis,
                nowMillis - properties.getEntryTtl().toMillis(), properties.getEntryTtl().toSeconds(),
                capacity());
    }

    @Override
    public BookingQueueStatusResponse status(Long userId, String token, Instant now) {
        EntryIdentity identity = identity(token);
        long nowMillis = now.toEpochMilli();
        return executeStatus(STATUS_SCRIPT, List.of(
                        queueKey(identity.slotId()), entryKey(token), userKey(identity.slotId(), userId),
                        throughputKey(identity.slotId(), now)
                ),
                userId, token, nowMillis - properties.getEntryTtl().toMillis(), nowMillis, capacity(),
                properties.getAdmissionTtl().toSeconds(), properties.getEntryTtl().toSeconds(),
                ENTRY_PREFIX, USER_PREFIX);
    }

    @Override
    public BookingQueueClaim claim(Long userId, String token, Instant now) {
        EntryIdentity identity = identity(token);
        String raw = execute(CLAIM_SCRIPT, List.of(
                        entryKey(token), queueKey(identity.slotId()), userKey(identity.slotId(), userId)
                ), userId, token, now.toEpochMilli(), properties.getProcessingTtl().toSeconds());
        JsonNode json = json(raw);
        String state = text(json, "status");
        checkExceptionalState(state);
        BookingQueueState queueState = BookingQueueState.valueOf(state);
        if (queueState == BookingQueueState.COMPLETED) {
            return new BookingQueueClaim(queueState, false, null, text(json, "reservation"));
        }
        boolean claimed = json.path("claimed").asBoolean(false);
        if (!claimed) return new BookingQueueClaim(queueState, false, null, null);
        return new BookingQueueClaim(queueState, true, new CreateTicketReservationRequest(
                json.path("tripId").asLong(), json.path("slotId").asLong(),
                json.path("quantity").asInt(), text(json, "requestKey")
        ), null);
    }

    @Override
    public void complete(Long userId, String token, TicketReservationDTO reservation, Instant now) {
        EntryIdentity identity = identity(token);
        try {
            Long result = redisTemplate.execute(COMPLETE_SCRIPT, List.of(
                            entryKey(token), queueKey(identity.slotId()), userKey(identity.slotId(), userId)
                    ), String.valueOf(userId), objectMapper.writeValueAsString(reservation),
                    String.valueOf(now.toEpochMilli()), String.valueOf(properties.getCompletedTtl().toSeconds()), token);
            checkMutationResult(result);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void release(Long userId, String token, Instant now) {
        EntryIdentity identity = identity(token);
        Long result = executeLong(RELEASE_SCRIPT, List.of(entryKey(token), userKey(identity.slotId(), userId)),
                userId, now.toEpochMilli(), properties.getAdmissionTtl().toSeconds(), token);
        checkMutationResult(result);
    }

    @Override
    public void cancel(Long userId, String token) {
        EntryIdentity identity = identity(token);
        Long result = executeLong(CANCEL_SCRIPT, List.of(
                entryKey(token), queueKey(identity.slotId()), userKey(identity.slotId(), userId)
        ), userId, token);
        checkMutationResult(result);
    }

    private BookingQueueStatusResponse executeStatus(
            DefaultRedisScript<String> script,
            List<String> keys,
            Object... arguments
    ) {
        JsonNode result = json(execute(script, keys, arguments));
        String state = text(result, "status");
        checkExceptionalState(state);
        BookingQueueState queueState = BookingQueueState.valueOf(state);
        int position = result.path("position").asInt(0);
        int initial = Math.max(position, result.path("initialPosition").asInt(position));
        int progress = queueState == BookingQueueState.READY || queueState == BookingQueueState.COMPLETED
                ? 100
                : initial == 0 ? 0 : Math.min(95, Math.max(0, (initial - position) * 100 / initial));
        long estimated = queueState == BookingQueueState.WAITING
                ? Math.max(1, (position + capacity() - 1L) / capacity())
                : 0;
        long expiresAt = result.path("expiresAt").asLong(0);
        return new BookingQueueStatusResponse(
                text(result, "token"), queueState, nullableLong(result, "slotId"), nullableLong(result, "tripId"),
                position, Math.max(0, position - 1), estimated, progress,
                expiresAt == 0 ? null : Instant.ofEpochMilli(expiresAt)
        );
    }

    private String execute(DefaultRedisScript<String> script, List<String> keys, Object... arguments) {
        try {
            String[] values = java.util.Arrays.stream(arguments).map(String::valueOf).toArray(String[]::new);
            String result = redisTemplate.execute(script, keys, (Object[]) values);
            if (result == null) throw new IllegalStateException("Redis script returned no result");
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private Long executeLong(DefaultRedisScript<Long> script, List<String> keys, Object... arguments) {
        try {
            String[] values = java.util.Arrays.stream(arguments).map(String::valueOf).toArray(String[]::new);
            return redisTemplate.execute(script, keys, (Object[]) values);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private EntryIdentity identity(String token) {
        try {
            Object slot = redisTemplate.opsForHash().get(entryKey(token), "slotId");
            if (slot == null) throw new BusinessException(ErrorCode.BOOKING_QUEUE_EXPIRED);
            return new EntryIdentity(Long.parseLong(slot.toString()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long nullableLong(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asLong();
    }

    private void checkExceptionalState(String state) {
        if (state == null) throw new BusinessException(ErrorCode.BOOKING_QUEUE_UNAVAILABLE);
        if ("FORBIDDEN".equals(state)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if ("EXPIRED".equals(state)) throw new BusinessException(ErrorCode.BOOKING_QUEUE_EXPIRED);
    }

    private void checkMutationResult(Long result) {
        if (result == null || result == 0) throw new BusinessException(ErrorCode.BOOKING_QUEUE_EXPIRED);
        if (result < 0) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private int capacity() {
        return Math.max(1, properties.getCapacityPerSecond());
    }

    private String queueKey(Long slotId) {
        return PREFIX + "slot:" + slotId + ":waiting";
    }

    private String entryKey(String token) {
        return ENTRY_PREFIX + token;
    }

    private String userKey(Long slotId, Long userId) {
        return USER_PREFIX + slotId + ":user:" + userId;
    }

    private String throughputKey(Long slotId, Instant now) {
        return PREFIX + "slot:" + slotId + ":admit:" + now.getEpochSecond();
    }

    private BusinessException unavailable(Exception cause) {
        return new BusinessException(ErrorCode.BOOKING_QUEUE_UNAVAILABLE);
    }

    private static DefaultRedisScript<String> script(String source) {
        return new DefaultRedisScript<>(source, String.class);
    }

    private static DefaultRedisScript<Long> longScript(String source) {
        return new DefaultRedisScript<>(source, Long.class);
    }

    private record EntryIdentity(Long slotId) {
    }
}
