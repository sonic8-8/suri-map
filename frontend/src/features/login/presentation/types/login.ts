export type LoginAccountType = 'TEAM' | 'PATROL_CAR' | 'COMMAND';

export type LoginOrganizationType = 'MISSING_TEAM' | 'SUPPORT_UNIT' | 'POLICE_SUBSTATION';

export type LoginRole = 'MISSING_TEAM_COMMANDER' | 'FIELD_COMMANDER' | 'MEMBER';

export type LoginAccount = {
  id: string;
  name: string;
  organization: string;
  rank: string;
  accountType: LoginAccountType;
  organizationType: LoginOrganizationType;
  role: LoginRole;
  roles: LoginRole[];
  description: string;
};
