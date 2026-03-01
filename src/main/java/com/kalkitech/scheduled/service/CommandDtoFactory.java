package com.kalkitech.scheduled.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kalkitech.scheduled.config.AppProperties;
import com.kalkitech.scheduled.model.MeterTarget;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
public class CommandDtoFactory {

    private final ObjectMapper om;
    private final AppProperties props;

    public enum JobType { INSTANT_5MIN, LOAD_30MIN }

    public CommandDtoFactory(ObjectMapper om, AppProperties props) {
        this.om = om;
        this.props = props;
    }

    public long epochSeconds(ZonedDateTime alignedTime) {
        return alignedTime.toEpochSecond();
    }

    public ObjectNode build(JobType type, long commandIdEpochSec, long fromEpochSec, long toEpochSec) {
        return build(type, commandIdEpochSec, fromEpochSec, toEpochSec, null);
    }

    /**
     * Builds the request body. If {@code target} is null, falls back to app.api.* properties.
     */
    public ObjectNode build(JobType type, long commandIdEpochSec, long fromEpochSec, long toEpochSec, MeterTarget target) {
        MeterTarget t = (target != null)
                ? target
                : new MeterTarget(props.getApi().getMeterNumber(), props.getApi().getMeterMake(), props.getApi().getIp());
        ObjectNode n = om.createObjectNode();
        n.put("commandId", commandIdEpochSec);

        if (type == JobType.INSTANT_5MIN) {
            n.put("commandType", "P_READ_INSTANT");
            n.put("subType", "INSTANT");
        } else {
            n.put("commandType", "P_READ_LOAD");
            n.put("subType", "LOAD");
        }

        n.put("count", 0);
        n.put("from", fromEpochSec);
        n.put("to", toEpochSec);

        n.put("ip", t.ip());
        n.put("meterMake", t.meterMake());
        n.put("meterNumber", t.meterNumber());

        return n;
    }
}
