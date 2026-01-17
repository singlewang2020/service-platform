package com.ruler.one.core.dispatcher;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TypeBasedDispatcherTest {

    @Test
    void should_dispatch_to_correct_handler() {
        CmdAHandler aHandler = new CmdAHandler();
        CmdBHandler bHandler = new CmdBHandler();

        TypeBasedDispatcher<TestCommand> dispatcher =
                new TypeBasedDispatcher<>(Map.of(
                        "aHandler", aHandler,
                        "bHandler", bHandler
                ));

        dispatcher.dispatch(new CmdA());

        assertTrue(aHandler.called, "CmdAHandler should be called");
        assertFalse(bHandler.called, "CmdBHandler should NOT be called");
    }

    @Test
    void should_fail_fast_when_duplicate_handler_for_same_type() {
        CmdAHandler handler1 = new CmdAHandler();
        CmdAHandler handler2 = new CmdAHandler();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new TypeBasedDispatcher<TestCommand>(
                        Map.of(
                                "handler1", handler1,
                                "handler2", handler2
                        )
                )
        );

        assertTrue(
                ex.getMessage().contains("Duplicate handler"),
                "Exception message should indicate duplicate handler"
        );
    }

    @Test
    void should_throw_when_no_handler_found() {
        CmdAHandler aHandler = new CmdAHandler();

        TypeBasedDispatcher<TestCommand> dispatcher =
                new TypeBasedDispatcher<>(Map.of(
                        "aHandler", aHandler
                ));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.dispatch(new CmdB())
        );

        assertTrue(
                ex.getMessage().contains("No handler for type"),
                "Exception message should indicate missing handler"
        );
    }

}
