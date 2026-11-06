package ru.practicum.gwint_backend.domain.command;

public sealed interface DomainCommand permits PlayCardCommand, PassCommand, ReadyNextRoundCommand{

}
