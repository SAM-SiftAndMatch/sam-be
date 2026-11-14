-- Demo seed amounts were denominated in dollars but displayed as VND.
-- Normalize the seeded jobs, proposals, contract, payment and hourly rates to realistic VND values.
UPDATE jobs SET budget_min = 15000000, budget_max = 25000000
WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
UPDATE jobs SET budget_min = 8000000, budget_max = 15000000
WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

UPDATE proposals SET proposed_budget = 20000000
WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
UPDATE proposals SET proposed_budget = 12000000
WHERE id = 'dddddddd-dddd-dddd-dddd-dddddddddddd';

UPDATE contracts SET agreed_amount = 12000000
WHERE id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';
UPDATE payments SET amount = 12000000
WHERE contract_id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';

UPDATE freelancer_profiles SET hourly_rate = 350000
WHERE user_id = '33333333-3333-3333-3333-333333333333';
UPDATE freelancer_profiles SET hourly_rate = 300000
WHERE user_id = '44444444-4444-4444-4444-444444444444';
