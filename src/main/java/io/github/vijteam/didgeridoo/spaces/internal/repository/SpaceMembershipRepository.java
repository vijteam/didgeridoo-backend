package io.github.vijteam.didgeridoo.spaces.internal.repository;

import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembershipId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceMembershipRepository
        extends JpaRepository<SpaceMembership, SpaceMembershipId> {}
