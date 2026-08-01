package com.gabrielfeijo.hookroute.application.port;

import com.gabrielfeijo.hookroute.application.IngestionCommand;
import com.gabrielfeijo.hookroute.application.IngestionResult;

public interface IngestionPort {
    IngestionResult ingest(IngestionCommand command);
}
