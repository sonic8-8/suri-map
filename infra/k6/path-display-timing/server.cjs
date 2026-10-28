// ssh로 접속할 서버 이름과 DB 컨테이너 이름. 측정 설정 JSON에 없으면 지금 측정 서버의 값을 쓴다.
const defaults = Object.freeze({ sshHost: 'hetzner-app', databaseContainer: 'suri-map-postgis' });
// ssh 옵션이나 원격 셸 문법으로 읽히지 않도록 이름에 쓰는 문자만 받는다.
const namePattern = /^[A-Za-z0-9][A-Za-z0-9._@-]{0,127}$/;

function serverOf(config = {}) {
  const server = { sshHost: config.sshHost ?? defaults.sshHost,
    databaseContainer: config.databaseContainer ?? defaults.databaseContainer };
  if (!Object.values(server).every(value => typeof value === 'string' && namePattern.test(value))) {
    throw Error('invalid_server_config');
  }
  return server;
}

function sshArgs(server, command) {
  return ['-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', server.sshHost, command];
}

function psqlCommand(server) {
  return `docker exec -i ${server.databaseContainer} sh -c 'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`;
}

module.exports = { defaults, serverOf, sshArgs, psqlCommand };
