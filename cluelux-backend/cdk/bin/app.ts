#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { ClueLuxBackendStack } from '../lib/cluelux-backend-stack';

const app = new cdk.App();

new ClueLuxBackendStack(app, 'ClueLuxBackendStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION || 'us-east-1',
  },
  description: 'Backend infrastructure for ClueLux game',
});

app.synth();
