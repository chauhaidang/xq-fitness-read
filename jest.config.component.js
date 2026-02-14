/**
 * Jest config for component tests.
 * Inlined from @chauhaidang/xq-test-utils (package exports don't expose subpath).
 */
module.exports = {
  displayName: 'Component Tests',
  preset: 'ts-jest',
  testEnvironment: 'node',
  rootDir: './',
  testMatch: ['<rootDir>/test/component/workflows/**/*.test.ts'],
  testTimeout: 60000,
  setupFilesAfterEnv: ['<rootDir>/test/component/setup.ts'],
  globalTeardown: '<rootDir>/test/component/teardown.ts',
  maxWorkers: 1,
  verbose: true,
  bail: false,
  collectCoverageFrom: [],
  moduleFileExtensions: ['ts', 'js', 'json'],
  moduleNameMapper: {
    '^@helpers/(.*)$': '<rootDir>/test/component/helpers/$1',
  },
  transform: {
    '^.+\\.ts$': [
      'ts-jest',
      {
        tsconfig: '<rootDir>/tsconfig.json',
      },
    ],
  },
};
