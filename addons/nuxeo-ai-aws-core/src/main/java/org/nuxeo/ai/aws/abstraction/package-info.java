/**
 * AWS Abstraction Layer - Complete isolation of AWS SDK dependencies
 *
 * This package provides a clean abstraction over AWS services that completely
 * isolates AWS SDK dependencies. Service implementations use only DTOs and
 * interfaces from this package, never importing AWS SDK classes directly.
 *
 * Benefits:
 * - Zero AWS SDK imports in service implementations
 * - Easy AWS SDK version upgrades (changes only in this package)
 * - Consistent request/response patterns across all AWS services
 * - Future-proof architecture for SDK changes
 *
 * @since 5.0.0
 */
package org.nuxeo.ai.aws.abstraction;

