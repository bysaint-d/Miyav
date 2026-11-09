-- The delete-account Edge Function calls the public wrappers as service_role; they run private.* as invoker.
grant usage on schema private to service_role;
