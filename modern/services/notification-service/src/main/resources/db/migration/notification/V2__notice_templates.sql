-- MAM-59: the notice templates. Subjects = the notice names of the Q-067 label table (default A; the business can
-- reword them, Q-A8). Bodies are short synthetic texts; {npi} is replaced when the notice is sent.
INSERT INTO notice_template (code, subject, body) VALUES
  ('APPROVED', 'Enrolment approved', 'The Medicaid enrolment of provider NPI {npi} has been approved.'),
  ('DENIED', 'Enrolment denied', 'The Medicaid enrolment of provider NPI {npi} has been denied.'),
  ('REJECTED', 'Application rejected', 'The enrolment application for NPI {npi} was rejected. Please correct it and apply again.'),
  ('REVALIDATION_DUE', 'Revalidation due', 'The Medicaid enrolment of provider NPI {npi} is due for revalidation.'),
  ('TRM', 'Termination notice', 'The Medicaid enrolment of provider NPI {npi} has been terminated.'),
  ('SUS', 'Suspension notice', 'The Medicaid enrolment of provider NPI {npi} has been suspended.'),
  ('RST', 'Reinstatement notice', 'The Medicaid enrolment of provider NPI {npi} has been reinstated.'),
  ('GEN', 'General notice', 'A notice about the Medicaid enrolment of provider NPI {npi}.')
ON CONFLICT (code) DO NOTHING;
