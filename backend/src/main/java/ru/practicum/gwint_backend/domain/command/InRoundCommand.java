package ru.practicum.gwint_backend.domain.command;

public sealed interface InRoundCommand extends DomainCommand permits PlayCardCommand, PassCommand{
}
