/**
 * Represents the RFC 7807 Problem Details for HTTP APIs.
 * This is the standard error format returned by the Spring Boot backend.
 */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;

  // Allow for extension properties if needed
  [key: string]: any;
}
